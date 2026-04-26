import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * Reports key status at a given date using the redesigned 2-table format.
 *
 * ── Usage ──────────────────────────────────────────────────────────────
 *
 * Linux / macOS:
 *   java -cp ~/works KeyStatusQuery 2024-03-31
 *
 *   # with explicit file paths:
 *   java -cp ~/works KeyStatusQuery 2024-03-31 \
 *       ~/Downloads/"【C-37】鍵管理簿.xlsx - 鍵マスター.tsv" \
 *       ~/Downloads/"【C-37】鍵管理簿.xlsx - 貸出履歴.tsv"
 *
 * Windows PowerShell:
 *   java -cp ~\works KeyStatusQuery 2024-03-31
 *
 *   # with explicit file paths:
 *   java -cp ~\works KeyStatusQuery 2024-03-31 `
 *       "~\Downloads\【C-37】鍵管理簿.xlsx - 鍵マスター.tsv" `
 *       "~\Downloads\【C-37】鍵管理簿.xlsx - 貸出履歴.tsv"
 *
 * Arguments:
 *   date        yyyy-MM-dd  (e.g. 2024-03-31)  required
 *   master-tsv  default: ~/Downloads/【C-37】鍵管理簿.xlsx - 鍵マスター.tsv
 *   history-tsv default: ~/Downloads/【C-37】鍵管理簿.xlsx - 貸出履歴.tsv
 *
 * ── 入力ファイルのフォーマット ────────────────────────────────────────
 *
 * 【鍵マスター.tsv】  1行目はヘッダー（読み飛ばし）
 *   col 0  鍵ID          一意な識別子（例: A-1, M-003）
 *   col 1  種類          鍵の種類（例: 共用, 個人）
 *   col 2  番号          通し番号・刻印番号など
 *   col 3  説明          鍵の用途・場所の説明
 *   col 4  保管場所      返却時の保管先
 *   col 5  属性          補足属性（任意）
 *
 * 【貸出履歴.tsv】  1行目はヘッダー（読み飛ばし）
 *   col 0  鍵ID          鍵マスターの鍵IDに対応
 *   col 1  借用者名      鍵を借りた人の氏名
 *   col 2  組織          借用者の所属組織
 *   col 3  役職          借用者の役職（個人鍵の場合）
 *   col 4  （未使用）
 *   col 5  貸出日        フォーマット: yyyy/MM/dd
 *   col 6  返却日        フォーマット: yyyy/MM/dd（未返却の場合は空欄）
 *   col 7  （未使用）
 *   col 8  作業内容      貸出目的・作業の説明
 *
 * ── BOM (Byte Order Mark) について ────────────────────────────────────
 *
 * Excel で「CSV UTF-8 (BOM 付き)」として保存すると、ファイル先頭に
 * 不可視の BOM バイト (EF BB BF) が付加される。このプログラムは先頭の
 * BOM を自動的に除去するので、そのまま読み込んで問題ない。
 *
 * ただし Excel で再保存するたびに BOM が付くため、TSV を直接編集する
 * 場合は VS Code / テキストエディタで「UTF-8 (BOM なし)」を選ぶと
 * 余計なトラブルを防げる。
 */
public class KeyStatusQuery {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private static final String BOM = "\uFEFF";

    record MasterKey(String id, String type, String number, String desc,
                     String storage, String attr) {}

    record LoanEvent(
            String id,
            String person,
            String org,
            String role,           // 役職
            String work,           // 作業内容
            LocalDate loanDate,
            LocalDate returnDate   // null = currently on loan
    ) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java KeyStatusQuery <date> [master-tsv] [history-tsv]");
            System.err.println("  date format: yyyy-MM-dd");
            System.err.println("  master-tsv  default: ~/Downloads/【C-37】鍵管理簿.xlsx - 鍵マスター.tsv");
            System.err.println("  history-tsv default: ~/Downloads/【C-37】鍵管理簿.xlsx - 貸出履歴.tsv");
            System.exit(1);
        }

        LocalDate queryDate = LocalDate.parse(args[0]);
        String downloads = System.getProperty("user.home") + "/Downloads/";
        Path masterPath  = Path.of(args.length > 1 ? args[1] : downloads + "【C-37】鍵管理簿.xlsx - 鍵マスター.tsv");
        Path historyPath = Path.of(args.length > 2 ? args[2] : downloads + "【C-37】鍵管理簿.xlsx - 貸出履歴.tsv");

        Map<String, MasterKey>        masterKeys = loadMaster(masterPath);
        Map<String, List<LoanEvent>>  history    = loadHistory(historyPath);
        printStatus(masterKeys, history, queryDate);
    }

    private static Map<String, MasterKey> loadMaster(Path path) throws IOException {
        Map<String, MasterKey> result = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
        if (!lines.isEmpty() && lines.get(0).startsWith(BOM))
            lines.set(0, lines.get(0).substring(1));
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split("\t", -1);
            if (c.length < 1 || col(c, 0).isEmpty()) continue;
            result.put(col(c, 0), new MasterKey(
                    col(c, 0), col(c, 1), col(c, 2), col(c, 3), col(c, 4), col(c, 5)));
        }
        return result;
    }

    private static Map<String, List<LoanEvent>> loadHistory(Path path) throws IOException {
        Map<String, List<LoanEvent>> result = new LinkedHashMap<>();
        List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
        if (!lines.isEmpty() && lines.get(0).startsWith(BOM))
            lines.set(0, lines.get(0).substring(1));
        for (int i = 1; i < lines.size(); i++) {
            String[] c = lines.get(i).split("\t", -1);
            if (c.length < 6 || col(c, 0).isEmpty()) continue;
            LocalDate loanDate = parseDate(col(c, 5));
            if (loanDate == null) continue;
            result.computeIfAbsent(col(c, 0), k -> new ArrayList<>())
                  .add(new LoanEvent(col(c, 0), col(c, 1), col(c, 2),
                          col(c, 3), col(c, 8),
                          loanDate, parseDate(col(c, 6))));
        }
        return result;
    }

    private static void printStatus(Map<String, MasterKey> masterKeys,
                                    Map<String, List<LoanEvent>> history,
                                    LocalDate queryDate) {
        System.out.println(String.join("\t",
                "鍵ID", "種類", "現在地", "貸出先", "状態", "貸出日", "返却日", "組織", "役職", "作業内容"));

        int activeCount = 0, returnedCount = 0, storedCount = 0;

        List<MasterKey> sorted = new ArrayList<>(masterKeys.values());
        sorted.sort(Comparator.comparing(k -> k.id(), KeyStatusQuery::naturalCompare));

        for (MasterKey key : sorted) {
            List<LoanEvent> events = history.getOrDefault(key.id(), List.of());

            // Currently on loan?
            LoanEvent active = events.stream()
                    .filter(e -> !e.loanDate().isAfter(queryDate))
                    .filter(e -> e.returnDate() == null || e.returnDate().isAfter(queryDate))
                    .max(Comparator.comparing(LoanEvent::loanDate))
                    .orElse(null);

            if (active != null) {
                printRow(key.id(), key.type(), active.person(), active.person(), "貸出中",
                        active.loanDate(), null,
                        active.org(), active.role(), active.work());
                activeCount++;
                continue;
            }

            LoanEvent latest = events.stream()
                    .filter(e -> !e.loanDate().isAfter(queryDate))
                    .max(Comparator.comparing(LoanEvent::loanDate))
                    .orElse(null);

            if (latest != null) {
                printRow(key.id(), key.type(), latest.person(), key.storage(), "返却済",
                        latest.loanDate(), latest.returnDate(),
                        latest.org(), latest.role(), latest.work());
                returnedCount++;
            } else {
                printRow(key.id(), key.type(), "", key.storage(), "保管中",
                        null, null, "", "", "");
                storedCount++;
            }
        }

        System.out.printf("%n# 鍵の状態: %s 時点%n合計\t貸出中 %d件\t返却済み %d件\t保管中 %d件%n", queryDate,
                activeCount, returnedCount, storedCount);

    }

    private static void printRow(String id, String type, String name, String location,
                                 String status, LocalDate loanDate, LocalDate returnDate,
                                 String org, String role, String work) {
        System.out.println(String.join("\t",
                id, type, location, name, status,
                loanDate   != null ? loanDate.toString()   : "",
                returnDate != null ? returnDate.toString() : "",
                org, role, work));
    }

    private static int naturalCompare(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int numStart_a = i, numStart_b = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                long na = Long.parseLong(a.substring(numStart_a, i));
                long nb = Long.parseLong(b.substring(numStart_b, j));
                if (na != nb) return Long.compare(na, nb);
            } else {
                if (ca != cb) return Character.compare(ca, cb);
                i++; j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String col(String[] cols, int i) {
        if (i >= cols.length) return "";
        return cols[i].strip();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        int width = 0;
        for (char c : s.toCharArray()) {
            int cw = c > 0x7F ? 2 : 1;
            if (width + cw > max) { sb.append("…"); break; }
            sb.append(c);
            width += cw;
        }
        while (width < max) { sb.append(' '); width++; }
        return sb.toString();
    }
}
