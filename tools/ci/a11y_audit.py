"""Lists what TalkBack couldn't name on one screen of the screenshot tour.

Usage: python3 -I a11y_audit.py <window dump .xml> <app package>

Reads a UiAutomator window dump (the tour writes one per shot when run with
"-e a11y true") and prints every clickable or long-clickable view of the app
with no words for TalkBack to read: no text or content description of its
own, and none on any view inside it. Prints nothing when every one has a
name. Never fails the run: it reports.
"""
import sys
import xml.etree.ElementTree as ET


def named(node):
    if node.get("text", "").strip() or node.get("content-desc", "").strip():
        return True
    return any(named(child) for child in node.findall("node"))


def main(path, package):
    tree = ET.parse(path)
    shot = path.rsplit("/", 1)[-1].removesuffix(".xml")
    unnamed = []
    for node in tree.iter("node"):
        if node.get("package") != package:
            continue
        acts = node.get("clickable") == "true" or node.get("long-clickable") == "true"
        if acts and node.get("enabled") == "true" and not named(node):
            unnamed.append(node)
    for node in unnamed:
        print(
            f"A11Y {shot}: {node.get('class')} {node.get('resource-id') or '(no id)'} "
            f"at {node.get('bounds')} has no name"
        )
    print(f"A11Y {shot}: {len(unnamed)} unnamed of the app's clickable views")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
