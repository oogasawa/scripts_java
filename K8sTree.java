import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;

/**
 * K8sTree - Visualize Kubernetes cluster state in a tree format.
 *
 * Single-file Java CLI tool (JDK 11+, no external dependencies).
 * Displays namespaces, deployments, services, PVCs, gateways, and HTTP routes
 * in a tree layout suitable for terminal display or markdown embedding.
 *
 * Features:
 *   - Replica status: Deployments/StatefulSets show (ready/desired), e.g. (1/1) or (0/0)
 *   - Blue/Green detection: HTTPRoutes with multiple backendRefs show weights, e.g. [B/G: unit1=0, unit2=100 ★]
 *   - Resource type labels: ns, deploy, sts, svc, pvc, gw, route
 *   - Auto-invokes kubectl when stdin is a terminal; reads piped JSON otherwise
 *
 * Usage:
 *   java K8sTree.java                  # auto-invoke kubectl, show all namespaces
 *   java K8sTree.java --ns             # list namespace names only
 *   java K8sTree.java --ns --no-infra  # list namespace names, excluding infra
 *   java K8sTree.java -n sc-account    # show a specific namespace
 *   java K8sTree.java --no-infra       # hide infrastructure namespaces (kube-system, longhorn-system, etc.)
 *   java K8sTree.java --help           # show help
 *
 *   # or pipe kubectl output directly:
 *   kubectl get deploy,sts,svc,pvc,httproute,gw -A -o json | java K8sTree.java
 */
public class K8sTree {

    // Infrastructure namespaces hidden with --no-infra
    private static final Set<String> INFRA_NS = Set.of(
        "kube-system", "kube-public", "kube-node-lease",
        "longhorn-system", "envoy-gateway-system", "ingress"
    );

    // Resource kind -> short label
    private static final Map<String, String> KIND_LABEL = new LinkedHashMap<>();
    static {
        KIND_LABEL.put("Deployment", "deploy");
        KIND_LABEL.put("StatefulSet", "sts");
        KIND_LABEL.put("Service", "svc");
        KIND_LABEL.put("PersistentVolumeClaim", "pvc");
        KIND_LABEL.put("Gateway", "gw");
        KIND_LABEL.put("HTTPRoute", "route");
    }

    // Display order for resource kinds within a namespace
    private static final List<String> KIND_ORDER = List.of(
        "Deployment", "StatefulSet", "Gateway", "HTTPRoute", "Service", "PersistentVolumeClaim"
    );

    public static void main(String[] args) throws Exception {
        var argList = Arrays.asList(args);
        boolean noInfra = argList.contains("--no-infra");
        boolean nsOnly = argList.contains("--ns");
        boolean help = argList.contains("--help") || argList.contains("-h");
        String filterNs = null;
        for (int i = 0; i < args.length; i++) {
            if ("-n".equals(args[i]) && i + 1 < args.length) {
                filterNs = args[i + 1];
            }
        }

        if (help) {
            System.out.println("Usage:");
            System.out.println("  java K8sTree.java              # show all namespaces");
            System.out.println("  java K8sTree.java --no-infra   # hide infra namespaces");
            System.out.println("  java K8sTree.java --ns         # list namespace names only");
            System.out.println("  java K8sTree.java -n <ns>      # show a specific namespace");
            System.out.println("  kubectl get deploy,svc,pvc,httproute,gw,sts -A -o json | java K8sTree.java");
            return;
        }

        String json = readInput();
        List<Map<String, String>> resources = parseItems(json);

        // Collect Blue/Green weight info from HTTPRoute rules
        Map<String, String> bgInfo = parseBgWeights(json);

        // Group by namespace, then by kind; store display name with replica info
        Map<String, Map<String, List<String>>> tree = new TreeMap<>();
        for (var r : resources) {
            String ns = r.get("namespace");
            if (ns == null || ns.isEmpty()) ns = "(cluster)";
            if (noInfra && INFRA_NS.contains(ns)) continue;
            if (filterNs != null && !filterNs.equals(ns)) continue;

            String kind = r.get("kind");
            String name = r.get("name");
            String replicas = r.get("replicas");
            String displayName = replicas != null ? name + " (" + replicas + ")" : name;

            tree.computeIfAbsent(ns, k -> new LinkedHashMap<>())
                .computeIfAbsent(kind, k -> new ArrayList<>())
                .add(displayName);
        }

        // --ns mode: list namespace names only
        if (nsOnly) {
            for (String ns : tree.keySet()) {
                System.out.println(ns);
            }
            return;
        }

        // Print tree
        var namespaces = new ArrayList<>(tree.keySet());
        for (int ni = 0; ni < namespaces.size(); ni++) {
            String ns = namespaces.get(ni);
            boolean lastNs = (ni == namespaces.size() - 1);
            String nsPrefix = lastNs ? "└── " : "├── ";
            String nsIndent = lastNs ? "    " : "│   ";

            System.out.println(nsPrefix + "ns: " + ns);

            var kindMap = tree.get(ns);
            // Sort kinds by defined order
            var kinds = kindMap.keySet().stream()
                .sorted(Comparator.comparingInt(k -> {
                    int idx = KIND_ORDER.indexOf(k);
                    return idx >= 0 ? idx : KIND_ORDER.size();
                }))
                .toList();

            for (int ki = 0; ki < kinds.size(); ki++) {
                String kind = kinds.get(ki);
                boolean lastKind = (ki == kinds.size() - 1);
                String kindPrefix = lastKind ? "└── " : "├── ";
                String kindIndent = lastKind ? "    " : "│   ";
                String label = KIND_LABEL.getOrDefault(kind, kind.toLowerCase());

                var names = kindMap.get(kind);
                Collections.sort(names);

                for (int ri = 0; ri < names.size(); ri++) {
                    String name = names.get(ri);
                    boolean lastRes = (ri == names.size() - 1);
                    String resPrefix = lastRes ? "└── " : "├── ";

                    // Build annotation (use raw name without replica suffix for B/G key)
                    String annotation = "";
                    String rawName = name.contains(" (") ? name.substring(0, name.indexOf(" (")) : name;
                    String bgKey = ns + "/" + rawName;
                    if (bgInfo.containsKey(bgKey)) {
                        annotation = "  " + bgInfo.get(bgKey);
                    }

                    if (ri == 0) {
                        // First resource: print kind header + first item on same branch
                        if (names.size() == 1) {
                            System.out.println(nsIndent + kindPrefix + label + ": " + name + annotation);
                        } else {
                            System.out.println(nsIndent + kindPrefix + label + "/");
                            System.out.println(nsIndent + kindIndent + resPrefix + name + annotation);
                        }
                    } else {
                        System.out.println(nsIndent + kindIndent + resPrefix + name + annotation);
                    }
                }
            }
        }
    }

    /** Read JSON from stdin (if piped), or auto-invoke kubectl (if stdin is a terminal). */
    private static String readInput() throws Exception {
        if (isStdinPiped()) {
            return new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // stdin is a terminal; auto-invoke kubectl
        var kinds = "deployments,statefulsets,services,pvc,httproute,gateway";
        var pb = new ProcessBuilder("kubectl", "get", kinds,
            "--all-namespaces", "-o", "json");
        pb.redirectErrorStream(true);
        var proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int rc = proc.waitFor();
        if (rc != 0) {
            System.err.println("kubectl failed (exit " + rc + "):\n" + out);
            System.exit(1);
        }
        return out;
    }

    /** Check if stdin is a pipe (not a terminal) using /proc/self/fd/0. */
    private static boolean isStdinPiped() {
        try {
            var link = java.nio.file.Files.readSymbolicLink(
                java.nio.file.Path.of("/proc/self/fd/0"));
            return link.toString().contains("pipe");
        } catch (Exception e) {
            // Fallback: if we can't check, assume terminal
            return false;
        }
    }

    // ---- Minimal JSON parsing (no dependencies) ----

    /** Extract items from kubectl JSON output. */
    private static List<Map<String, String>> parseItems(String json) {
        List<Map<String, String>> result = new ArrayList<>();
        // Find each item in the "items" array
        int itemsStart = json.indexOf("\"items\"");
        if (itemsStart < 0) return result;

        int arrStart = json.indexOf('[', itemsStart);
        if (arrStart < 0) return result;

        // Find each top-level object in the array
        int depth = 0;
        int objStart = -1;
        for (int i = arrStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') { i = skipString(json, i); continue; }
            if (c == '[' && depth == 0) { depth = 1; continue; }
            if (depth == 1 && c == '{') { objStart = i; depth = 2; }
            else if (depth >= 2 && c == '{') { depth++; }
            else if (depth >= 2 && c == '}') {
                depth--;
                if (depth == 1 && objStart >= 0) {
                    String obj = json.substring(objStart, i + 1);
                    var r = extractResource(obj);
                    if (r != null) result.add(r);
                    objStart = -1;
                }
            }
            if (depth == 1 && c == ']') break;
        }
        return result;
    }

    /** Extract kind, namespace, name, and replica info from a single resource JSON object. */
    private static Map<String, String> extractResource(String obj) {
        String kind = extractField(obj, "kind");
        if (kind == null) return null;

        // Find metadata block
        int metaIdx = obj.indexOf("\"metadata\"");
        if (metaIdx < 0) return null;
        int braceStart = obj.indexOf('{', metaIdx);
        if (braceStart < 0) return null;
        int braceEnd = findMatchingBrace(obj, braceStart);
        String meta = obj.substring(braceStart, braceEnd + 1);

        String name = extractField(meta, "name");
        String namespace = extractField(meta, "namespace");

        var map = new HashMap<String, String>();
        map.put("kind", kind);
        map.put("name", name != null ? name : "");
        map.put("namespace", namespace != null ? namespace : "");

        // Extract replica status for Deployment and StatefulSet
        if ("Deployment".equals(kind) || "StatefulSet".equals(kind)) {
            // spec.replicas (desired)
            int specIdx = findTopLevelKey(obj, "spec");
            String desired = "1"; // k8s default
            if (specIdx >= 0) {
                int specBrace = obj.indexOf('{', specIdx);
                if (specBrace >= 0) {
                    String specBlock = obj.substring(specBrace, findMatchingBrace(obj, specBrace) + 1);
                    String r = extractNumberField(specBlock, "replicas");
                    if (r != null) desired = r;
                }
            }

            // status.readyReplicas
            int statusIdx = findTopLevelKey(obj, "status");
            String ready = "0";
            if (statusIdx >= 0) {
                int statusBrace = obj.indexOf('{', statusIdx);
                if (statusBrace >= 0) {
                    String statusBlock = obj.substring(statusBrace, findMatchingBrace(obj, statusBrace) + 1);
                    String rr = extractNumberField(statusBlock, "readyReplicas");
                    if (rr != null) ready = rr;
                }
            }
            map.put("replicas", ready + "/" + desired);
        }
        return map;
    }

    /** Parse Blue/Green weights from HTTPRoute backendRefs. */
    private static Map<String, String> parseBgWeights(String json) {
        Map<String, String> result = new HashMap<>();
        List<Map<String, String>> resources = new ArrayList<>();

        int itemsStart = json.indexOf("\"items\"");
        if (itemsStart < 0) return result;
        int arrStart = json.indexOf('[', itemsStart);
        if (arrStart < 0) return result;

        int depth = 0;
        int objStart = -1;
        for (int i = arrStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') { i = skipString(json, i); continue; }
            if (c == '[' && depth == 0) { depth = 1; continue; }
            if (depth == 1 && c == '{') { objStart = i; depth = 2; }
            else if (depth >= 2 && c == '{') { depth++; }
            else if (depth >= 2 && c == '}') {
                depth--;
                if (depth == 1 && objStart >= 0) {
                    String obj = json.substring(objStart, i + 1);
                    String kind = extractField(obj, "kind");
                    if ("HTTPRoute".equals(kind)) {
                        parseRouteWeights(obj, result);
                    }
                    objStart = -1;
                }
            }
            if (depth == 1 && c == ']') break;
        }
        return result;
    }

    /** Extract B/G weight annotations from a single HTTPRoute object. */
    private static void parseRouteWeights(String obj, Map<String, String> result) {
        int metaIdx = obj.indexOf("\"metadata\"");
        if (metaIdx < 0) return;
        int braceStart = obj.indexOf('{', metaIdx);
        String meta = obj.substring(braceStart, findMatchingBrace(obj, braceStart) + 1);
        String ns = extractField(meta, "namespace");
        String routeName = extractField(meta, "name");

        // Find backendRefs arrays and check for multiple refs with weight
        int searchFrom = 0;
        while (true) {
            int brIdx = obj.indexOf("\"backendRefs\"", searchFrom);
            if (brIdx < 0) break;
            int arrIdx = obj.indexOf('[', brIdx);
            if (arrIdx < 0) break;
            int arrEnd = findMatchingBracket(obj, arrIdx);
            String refsBlock = obj.substring(arrIdx, arrEnd + 1);
            searchFrom = arrEnd + 1;

            // Count refs and extract weights
            List<String[]> refs = new ArrayList<>(); // [name, weight]
            int rDepth = 0;
            int rObjStart = -1;
            for (int i = 0; i < refsBlock.length(); i++) {
                char c = refsBlock.charAt(i);
                if (c == '"') { i = skipString(refsBlock, i); continue; }
                if (c == '[' && rDepth == 0) { rDepth = 1; continue; }
                if (rDepth == 1 && c == '{') { rObjStart = i; rDepth = 2; }
                else if (rDepth >= 2 && c == '{') rDepth++;
                else if (rDepth >= 2 && c == '}') {
                    rDepth--;
                    if (rDepth == 1 && rObjStart >= 0) {
                        String refObj = refsBlock.substring(rObjStart, i + 1);
                        String refName = extractField(refObj, "name");
                        String weight = extractNumberField(refObj, "weight");
                        refs.add(new String[]{refName, weight != null ? weight : "?"});
                        rObjStart = -1;
                    }
                }
                if (rDepth == 1 && c == ']') break;
            }

            if (refs.size() >= 2) {
                // This is a Blue/Green route
                String annotation = refs.stream()
                    .map(r -> {
                        String w = r[1];
                        String marker = "100".equals(w) ? " ★" : "";
                        return r[0] + "=" + w + marker;
                    })
                    .collect(Collectors.joining(", ", "[B/G: ", "]"));

                result.put(ns + "/" + routeName, annotation);
            }
        }
    }

    /** Find a top-level key in a JSON object (depth 1 only, skipping nested objects). */
    private static int findTopLevelKey(String json, String field) {
        String key = "\"" + field + "\"";
        int depth = 0;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                if (depth == 1) {
                    // Check if this is our key
                    if (json.startsWith(key, i)) {
                        return i;
                    }
                }
                i = skipString(json, i);
                continue;
            }
            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;
        }
        return -1;
    }

    /** Extract a string field value from a JSON object (first occurrence). */
    private static String extractField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = 0;
        while (true) {
            idx = json.indexOf(key, idx);
            if (idx < 0) return null;
            // Make sure this is a key (followed by colon after optional whitespace)
            int afterKey = idx + key.length();
            int ci = afterKey;
            while (ci < json.length() && Character.isWhitespace(json.charAt(ci))) ci++;
            if (ci < json.length() && json.charAt(ci) == ':') {
                // Skip to value
                ci++;
                while (ci < json.length() && Character.isWhitespace(json.charAt(ci))) ci++;
                if (ci < json.length() && json.charAt(ci) == '"') {
                    int valStart = ci + 1;
                    int valEnd = json.indexOf('"', valStart);
                    // Handle escaped quotes
                    while (valEnd > 0 && json.charAt(valEnd - 1) == '\\') {
                        valEnd = json.indexOf('"', valEnd + 1);
                    }
                    if (valEnd > 0) return json.substring(valStart, valEnd);
                }
            }
            idx = afterKey;
        }
    }

    /** Extract a numeric field value from a JSON object. */
    private static String extractNumberField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int ci = idx + key.length();
        while (ci < json.length() && Character.isWhitespace(json.charAt(ci))) ci++;
        if (ci >= json.length() || json.charAt(ci) != ':') return null;
        ci++;
        while (ci < json.length() && Character.isWhitespace(json.charAt(ci))) ci++;
        int start = ci;
        while (ci < json.length() && (Character.isDigit(json.charAt(ci)) || json.charAt(ci) == '-')) ci++;
        if (ci > start) return json.substring(start, ci);
        return null;
    }

    /** Skip past a JSON string starting at the opening quote. Returns index of closing quote. */
    private static int skipString(String json, int openQuote) {
        for (int i = openQuote + 1; i < json.length(); i++) {
            if (json.charAt(i) == '\\') { i++; continue; }
            if (json.charAt(i) == '"') return i;
        }
        return json.length() - 1;
    }

    /** Find matching closing brace. */
    private static int findMatchingBrace(String json, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') { i = skipString(json, i); continue; }
            if (c == '{') depth++;
            if (c == '}') { depth--; if (depth == 0) return i; }
        }
        return json.length() - 1;
    }

    /** Find matching closing bracket. */
    private static int findMatchingBracket(String json, int openBracket) {
        int depth = 0;
        for (int i = openBracket; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') { i = skipString(json, i); continue; }
            if (c == '[') depth++;
            if (c == ']') { depth--; if (depth == 0) return i; }
        }
        return json.length() - 1;
    }
}
