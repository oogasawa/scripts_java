#!/usr/bin/env python3
"""
Converts the current key management TSV files to a clean 2-table format:
  - 鍵マスター.tsv  : authoritative key inventory (one row per key)
  - 貸出履歴.tsv    : loan events (one row per loan)
"""
import csv
import re

LEDGER  = "/home/devteam/Downloads/【C-37】鍵管理簿.xlsx - 鍵管理簿(2019_4_1～) (1).tsv"
GENKYOU = "/home/devteam/Downloads/【C-37】鍵管理簿.xlsx - 現況（E列1抽出）.tsv"
OUT_MASTER  = "/home/devteam/Downloads/鍵マスター.tsv"
OUT_HISTORY = "/home/devteam/Downloads/貸出履歴.tsv"


def read_tsv(path):
    with open(path, encoding="utf-8") as f:
        reader = csv.reader(f, delimiter="\t")
        headers = next(reader)
        return headers, list(reader)


def main():
    # ── 鍵マスター ────────────────────────────────────────────────
    # Source: 現況 (one row per key, authoritative inventory)
    # Columns: 鍵ID, 種類, 番号, 説明, 属性
    _, genkyou_rows = read_tsv(GENKYOU)

    master_headers = ["鍵ID", "種類", "番号", "説明", "属性"]
    master_rows = []
    for row in genkyou_rows:
        if not row or not row[0].strip():
            continue
        master_rows.append([
            row[0].strip(),   # 鍵の統合ID
            row[1].strip(),   # 鍵の種類
            row[2].strip(),   # 鍵番号
            row[3].strip(),   # 説明
            row[29].strip() if len(row) > 29 else "",  # 属性
        ])

    with open(OUT_MASTER, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        w.writerow(master_headers)
        w.writerows(master_rows)

    print(f"鍵マスター: {len(master_rows)} 件 → {OUT_MASTER}")

    # ── 貸出履歴 ────────────────────────────────────────────────
    # Source: 管理簿 (all rows that have a loanDate — actual loan events)
    # Columns: 鍵ID, 氏名, 組織, 役職, 部門, 貸出日, 返却日
    _, ledger_rows = read_tsv(LEDGER)

    # Columns: 鍵ID, 氏名, 組織, 役職, 部門, 委託担当者, 貸出日, 返却日, 作業内容
    history_headers = ["鍵ID", "氏名", "組織", "役職", "部門", "委託担当者", "貸出日", "返却日", "作業内容"]
    history_rows = []
    skipped = 0
    for row in ledger_rows:
        if not row or not row[0].strip():
            continue
        loan_date = row[13].strip() if len(row) > 13 else ""
        return_date = row[14].strip() if len(row) > 14 else ""
        if not loan_date and not return_date:
            skipped += 1
            continue
        if not loan_date:
            loan_date = "2019/4/1"
        key_id = row[0].strip()
        if key_id.startswith("物理鍵:"):
            name      = row[17].strip() if len(row) > 17 else ""  # 作業者
            org       = ""
            role      = ""
            dept      = ""
            custodian = row[16].strip() if len(row) > 16 else ""  # 委託業者の鍵貸出担当者
            work      = row[18].strip() if len(row) > 18 else ""  # 作業内容
        elif key_id.startswith("セコム:"):
            raw = row[3].strip()
            m = re.search(r'_使用_(.+)', raw)
            if m:
                name = m.group(1).replace("iNONロッカー", "").replace("が携帯", "").strip()
            else:
                m2 = re.search(r'_予備_(.+)', raw)
                name = "予備（" + m2.group(1).strip() + "）" if m2 else raw
            org       = row[4].strip()
            role      = row[6].strip() if len(row) > 6 else ""
            dept      = row[7].strip() if len(row) > 7 else ""
            custodian = ""
            work      = ""
        else:
            name      = row[3].strip()
            org       = row[4].strip()
            role      = row[6].strip() if len(row) > 6 else ""
            dept      = row[7].strip() if len(row) > 7 else ""
            custodian = ""
            work      = ""
        history_rows.append([
            key_id, name, org, role, dept, custodian,
            loan_date,
            return_date,
            work,
        ])

    with open(OUT_HISTORY, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        w.writerow(history_headers)
        w.writerows(history_rows)

    print(f"貸出履歴: {len(history_rows)} 件 → {OUT_HISTORY}")
    print(f"  (貸出日なし行 {skipped} 件はスキップ)")


if __name__ == "__main__":
    main()
