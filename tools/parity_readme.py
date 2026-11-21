#!/usr/bin/env python3
"""Redraw the Nova parity tracker in README.md from docs/parity/nova-parity.csv.

Edit the CSV, then run:

    python3 tools/parity_readme.py          # rewrite README.md
    python3 tools/parity_readme.py --check  # fail if README.md is out of date

Only the text between the parity:start and parity:end markers is replaced.
Standard library only.
"""
import csv
import sys
from collections import Counter, OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CSV_PATH = ROOT / "docs" / "parity" / "nova-parity.csv"
README = ROOT / "README.md"
START = "<!-- parity:start -->"
END = "<!-- parity:end -->"

COLUMNS = ["Area", "Feature", "Since", "Prime", "Status", "Priority", "Nova behaviour", "Notes"]
STATUS_ICON = OrderedDict([
    ("Done", "✅"),
    ("Partial", "🟡"),
    ("Missing", "❌"),
    ("Check", "❔"),
    ("Won't do", "⛔"),
])
STATUS_LABEL = {
    "Done": "Done",
    "Partial": "Partial",
    "Missing": "Missing",
    "Check": "Needs a check on a device",
    "Won't do": "Won't do",
}
PRIORITIES = {"P0", "P1", "P2", "Skip"}


def load_rows():
    with open(CSV_PATH, newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        if reader.fieldnames != COLUMNS:
            sys.exit(f"{CSV_PATH}: expected columns {COLUMNS}, got {reader.fieldnames}")
        rows = list(reader)
    for number, row in enumerate(rows, start=2):
        if row["Status"] not in STATUS_ICON:
            sys.exit(f"{CSV_PATH}:{number}: unknown status {row['Status']!r}")
        if row["Priority"] not in PRIORITIES:
            sys.exit(f"{CSV_PATH}:{number}: unknown priority {row['Priority']!r}")
        if row["Prime"] not in {"Yes", "No"}:
            sys.exit(f"{CSV_PATH}:{number}: Prime must be Yes or No")
    return rows


def cell(text):
    return text.replace("|", "\\|").strip()


def render(rows):
    areas = OrderedDict()
    for row in rows:
        areas.setdefault(row["Area"], []).append(row)
    totals = Counter(row["Status"] for row in rows)

    out = [START, "", "| Status | Features |", "| --- | --- |"]
    for status, icon in STATUS_ICON.items():
        out.append(f"| {icon} {STATUS_LABEL[status]} | {totals.get(status, 0)} |")
    out.append(f"| **Total** | **{len(rows)}** |")
    out.append("")

    for area, items in areas.items():
        counts = Counter(row["Status"] for row in items)
        tally = " · ".join(
            f"{icon} {counts[status]}" for status, icon in STATUS_ICON.items() if counts.get(status)
        )
        out += [
            "<details>",
            f"<summary><b>{area}</b> · {len(items)} features · {tally}</summary>",
            "",
            "| Status | Feature | Since | Prime | Priority | What Nova did | Notes |",
            "| --- | --- | --- | --- | --- | --- | --- |",
        ]
        for row in items:
            out.append(
                "| {icon} | {feature} | {since} | {prime} | {priority} | {behaviour} | {notes} |".format(
                    icon=STATUS_ICON[row["Status"]],
                    feature=cell(row["Feature"]),
                    since=cell(row["Since"]),
                    prime="Prime" if row["Prime"] == "Yes" else "",
                    priority=row["Priority"],
                    behaviour=cell(row["Nova behaviour"]),
                    notes=cell(row["Notes"]),
                )
            )
        out += ["", "</details>", ""]
    out.append(END)
    return "\n".join(out)


def main():
    check = "--check" in sys.argv[1:]
    readme = README.read_text(encoding="utf-8")
    if START not in readme or END not in readme:
        sys.exit(f"{README}: missing {START} / {END} markers")
    head, rest = readme.split(START, 1)
    _, tail = rest.split(END, 1)
    updated = head + render(load_rows()) + tail
    if check:
        if updated != readme:
            sys.exit("README.md is out of date: run python3 tools/parity_readme.py")
        print("README.md parity tracker is up to date")
        return
    README.write_text(updated, encoding="utf-8")
    print(f"README.md: tracker redrawn from {CSV_PATH.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
