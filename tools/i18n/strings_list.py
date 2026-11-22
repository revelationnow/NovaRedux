"""The English strings.xml as a plain list to translate from.

Usage: python3 tools/i18n/strings_list.py app/src/main/res/values/strings.xml en.txt

One line per string, "name|text", in the file's order: entities decoded,
Android's escapes (\\' \\" \\@ \\?) undone, a newline kept as the two
characters backslash-n, inline markup (<u>) kept. A plural is one line per
quantity, "name#quantity|text". Strings marked translatable="false" are left
out. A translated copy of the list goes back through strings_build.py.
"""
import re
import sys
import xml.etree.ElementTree as ET

src, out = sys.argv[1], sys.argv[2]
raw = open(src, encoding="utf-8").read()


def inner(el):
    # The element's content as written, markup included.
    text = (el.text or "")
    for child in el:
        text += ET.tostring(child, encoding="unicode")
    return text


def plain(text):
    text = text.strip() if text.strip() != text and not text.strip().startswith('"') else text
    if text.startswith('"') and text.endswith('"') and len(text) >= 2:
        text = text[1:-1]
    text = re.sub(r"\\(['\"@?])", r"\1", text)
    return text


root = ET.fromstring(raw)
rows = []
for el in root:
    name = el.get("name")
    if el.get("translatable") == "false":
        continue
    if el.tag == "string":
        rows.append((name, plain(inner(el))))
    elif el.tag == "plurals":
        for item in el:
            rows.append((f"{name}#{item.get('quantity')}", plain(inner(item))))
with open(out, "w", encoding="utf-8") as f:
    for name, text in rows:
        assert "\n" not in text, (name, text)
        f.write(f"{name}|{text}\n")
print(len(rows), "rows,", sum(len(t) for _, t in rows), "characters of text")
