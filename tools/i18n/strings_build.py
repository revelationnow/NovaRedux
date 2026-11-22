"""Build values-<lang>/strings.xml from a translated list.

Usage: python3 tools/i18n/strings_build.py app/src/main/res/values/strings.xml \
           de.txt app/src/main/res/values-de/strings.xml [--machine]

The list has one line per string, "name|text", in plain text (as
strings_list.py writes the English): apostrophes and quotes as they are,
a newline as the two characters backslash-n, <u> markup kept. Plurals are
"name#quantity|text". A string left out shows in English.

Stops, writing nothing, on a name the English file lacks, a translation
whose placeholders differ from the English, or markup that doesn't match.
"""
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter

en_path, tr_path, out_path = sys.argv[1], sys.argv[2], sys.argv[3]
# --machine: say at the top of the file that a machine translated it.
machine = "--machine" in sys.argv[4:]

FORMAT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([sdfxXcb%])")
MARKUP = re.compile(r"</?[a-z]+>")
ALLOWED_TAGS = {"<u>", "</u>", "<b>", "</b>", "<i>", "</i>"}


def inner(el):
    text = el.text or ""
    for child in el:
        text += ET.tostring(child, encoding="unicode")
    return text


def plain(text):
    text = text.strip()
    if text.startswith('"') and text.endswith('"') and len(text) >= 2:
        text = text[1:-1]
    return re.sub(r"\\(['\"@?])", r"\1", text)


def formats(text, formatted=True):
    if not formatted:
        return Counter()
    found = Counter()
    for match in FORMAT.finditer(text):
        if match.group(2) == "%":
            continue
        found[(match.group(1), match.group(2))] += 1
    return found


def escape(text):
    out = []
    i = 0
    while i < len(text):
        tag = MARKUP.match(text, i)
        if tag and tag.group(0) in ALLOWED_TAGS:
            out.append(tag.group(0))
            i = tag.end()
            continue
        c = text[i]
        if c == "\\" and i + 1 < len(text) and text[i + 1] in "nt'\"u@?\\":
            # An Android escape written into the text: \n, \t, \u2013 and the like.
            out.append(text[i:i + 2])
            i += 2
            continue
        out.append({"&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "\\'", '"': '\\"'}.get(c, c))
        i += 1
    joined = "".join(out)
    if joined[:1] in ("@", "?"):
        joined = "\\" + joined
    return joined


english = ET.parse(en_path).getroot()
entries = []
en_text = {}
for el in english:
    if el.get("translatable") == "false":
        continue
    name = el.get("name")
    if el.tag == "string":
        entries.append(("string", name, el.get("formatted") != "false"))
        en_text[name] = plain(inner(el))
    elif el.tag == "plurals":
        entries.append(("plurals", name, True))
        for item in el:
            en_text[f"{name}#{item.get('quantity')}"] = plain(inner(item))

translated = {}
problems = []
for number, line in enumerate(open(tr_path, encoding="utf-8"), start=1):
    line = line.rstrip("\n")
    if not line.strip():
        continue
    if "|" not in line:
        problems.append(f"line {number}: no separator")
        continue
    name, text = line.split("|", 1)
    name = name.strip()
    if name in translated:
        problems.append(f"line {number}: {name} twice")
    translated[name] = text.strip()

plural_names = {name for kind, name, _ in entries if kind == "plurals"}
string_names = {name: fmt for kind, name, fmt in entries if kind == "string"}
for name, text in translated.items():
    base = name.split("#")[0]
    if "#" in name:
        if base not in plural_names:
            problems.append(f"{name}: no such plurals")
            continue
        reference = en_text.get(f"{base}#other")
        fmt = True
    else:
        if name not in string_names:
            problems.append(f"{name}: no such string")
            continue
        reference = en_text[name]
        fmt = string_names[name]
    if formats(text, fmt) != formats(reference, fmt):
        problems.append(f"{name}: placeholders {dict(formats(text, fmt))} vs English {dict(formats(reference, fmt))}")
    if sorted(MARKUP.findall(text)) != sorted(MARKUP.findall(reference)):
        problems.append(f"{name}: markup differs from the English")
    if text.count("%") != reference.count("%") and fmt:
        problems.append(f"{name}: a different number of % signs")
if problems:
    sys.exit("\n".join(problems))

lines = ['<?xml version="1.0" encoding="utf-8"?>']
if machine:
    lines.append("<!-- Machine-translated from the English; corrections welcome (docs/TRANSLATING.md). -->")
lines.append("<resources>")
count = 0
for kind, name, fmt in entries:
    if kind == "string":
        if name in translated:
            attr = "" if fmt else ' formatted="false"'
            lines.append(f'    <string name="{name}"{attr}>{escape(translated[name])}</string>')
            count += 1
    else:
        items = [(key.split("#")[1], text) for key, text in translated.items() if key.split("#")[0] == name]
        if items:
            order = ["zero", "one", "two", "few", "many", "other"]
            items.sort(key=lambda kv: order.index(kv[0]))
            lines.append(f'    <plurals name="{name}">')
            for quantity, text in items:
                lines.append(f'        <item quantity="{quantity}">{escape(text)}</item>')
            lines.append("    </plurals>")
            count += 1
lines.append("</resources>")
xml = "\n".join(lines) + "\n"
ET.fromstring(xml.encode("utf-8"))
open(out_path, "w", encoding="utf-8").write(xml)
total = len(entries)
print(f"{out_path}: {count} of {total} strings and plurals translated")
