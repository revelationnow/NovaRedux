#!/usr/bin/env python3
"""Supernova's names as NovaRedux names them (docs/NAMES.md), for porting.

Port one Supernova commit:

    git fetch https://github.com/tanujnotes/Supernova.git main
    python3 tools/upstream_names.py <commit> > port.patch
    git am -3 port.patch

The patch holds the commit's files before and after, each renamed as
NovaRedux names things, so git's three-way merge compares like with like:
what still conflicts is what NovaRedux changed itself, and resolves as in a
cherry-pick. The commit keeps its author and date. "(#322)" in its subject
becomes "(Supernova issue 322)", so it doesn't link to NovaRedux's own 322.

Read one of Supernova's files with NovaRedux's names:

    git show <commit>:<path> | python3 tools/upstream_names.py --text

The rules below are the ones docs/NAMES.md lists; a new rename goes in both.
"""
import os
import re
import subprocess
import sys
import tempfile

# Held by icon packs (their Apply button), so kept word for word.
KEPT = [
    "com.supernova.launcher.APPLY_ICON_THEME",
    "com.supernova.launcher.extra.ICON_THEME_PACKAGE",
]

CLASSES = [
    "SupernovaAccessibilityService",
    "SupernovaApp",
    "SupernovaDeviceAdmin",
    "SupernovaNotificationListener",
    "SupernovaPopupMenu",
    "SupernovaWidgetHostView",
    "SupernovaWidgetHost",
]

# (pattern, replacement, what it covers): applied in order, to file text and
# to paths alike. Supernova's prose ("Supernova issue 322") has none of
# these, so it stays as it is.
RULES = [
    (r"com\.supernova\.launcher", "com.novaredux.launcher", "package, and every class in it"),
    (r"com/supernova/launcher", "com/novaredux/launcher", "source folders and profile entries"),
    *[(r"\b" + name + r"\b", name.replace("Supernova", "NovaRedux"), "class and its file") for name in CLASSES],
    (r"\b(Theme|Widget|ThemeOverlay|TextAppearance|ShapeAppearanceOverlay)([._])Supernova(?![a-z])",
     r"\1\2NovaRedux", "styles, in XML and as R.style fields"),
    (r"\bic_supernova\b", "ic_novaredux", "drawable"),
    (r"\brestart_supernova", "restart_novaredux", "strings"),
    (r"\bsupernovaStore\b", "novaReduxStore", "settings store's Kotlin name"),
    (r"\bsupernovaLayoutStore\b", "novaReduxLayoutStore", "layout store's Kotlin name"),
    (r"\bsupernovaUsageStore\b", "novaReduxUsageStore", "usage store's Kotlin name"),
    (r"\bsupernova_(store|layout|usage)\b", r"novaredux_\1", "settings, layout and usage store files"),
    (r"\bsupernova_crash\.log\b", "novaredux_crash.log", "crash log file"),
    (r"\bsupernova://action/", "novaredux://action/", "launcher-action links"),
    (r"\bsupernova:app\b", "novaredux:app", "App icons marker"),
]
COMPILED = [(re.compile(pattern), repl) for pattern, repl, _ in RULES]


def rename(text):
    for i, kept in enumerate(KEPT):
        text = text.replace(kept, f"\0KEPT{i}\0")
    for pattern, repl in COMPILED:
        text = pattern.sub(repl, text)
    for i, kept in enumerate(KEPT):
        text = text.replace(f"\0KEPT{i}\0", kept)
    return text


def git(*args, data=None):
    return subprocess.run(["git", *args], check=True, capture_output=True, input=data).stdout


def blob(commit, path):
    return git("show", f"{commit}:{path}")


def mode(commit, path):
    line = git("ls-tree", commit, "--", path).decode().split()
    return int(line[0], 8) if line else 0o100644


def renamed(data):
    if b"\0" in data:
        return data
    try:
        return rename(data.decode("utf-8")).encode("utf-8")
    except UnicodeDecodeError:
        return data


def subject_of(subject):
    def issues(match):
        numbers = re.findall(r"#(\d+)", match.group(0))
        return "(Supernova issue" + ("s " if len(numbers) > 1 else " ") + ", ".join(numbers) + ")"
    return re.sub(r"\((?:\s*#\d+\s*)+\)", issues, subject)


def port(commit):
    parents = git("rev-list", "--parents", "-n", "1", commit).decode().split()[1:]
    if len(parents) != 1:
        sys.exit("upstream_names.py: give a commit with one parent")
    parent = parents[0]
    changes = git("diff", "--name-status", "--no-renames", parent, commit).decode().splitlines()
    with tempfile.TemporaryDirectory() as tmp:
        for line in changes:
            status, path = line.split("\t", 1)
            target = rename(path)
            sides = [("old", parent, status != "A"), ("new", commit, status != "D")]
            for side, rev, present in sides:
                if not present:
                    continue
                data = renamed(blob(rev, path))
                out = os.path.join(tmp, side, target)
                os.makedirs(os.path.dirname(out), exist_ok=True)
                with open(out, "wb") as f:
                    f.write(data)
                os.chmod(out, mode(rev, path) & 0o777)
                # Old sides go into the object store, for git am -3's merge.
                git("hash-object", "-w", "--stdin", data=data)
        os.makedirs(os.path.join(tmp, "old"), exist_ok=True)
        os.makedirs(os.path.join(tmp, "new"), exist_ok=True)
        diff = subprocess.run(
            ["git", "diff", "--no-index", "--binary", "--full-index", "--src-prefix=a/", "--dst-prefix=b/",
             "old", "new"],
            cwd=tmp, capture_output=True
        ).stdout.decode("utf-8", "surrogateescape")
    diff = re.sub(r"(?m)^diff --git a/(?:old|new)/(\S+) b/(?:old|new)/(\S+)$", r"diff --git a/\1 b/\2", diff)
    diff = re.sub(r"(?m)^(--- a/|\+\+\+ b/|rename from |rename to )(old|new)/", r"\1", diff)
    fields = git("log", "-1", "--format=%H%n%an%n%ae%n%aD%n%s%n%b", commit).decode().split("\n", 5)
    sha, name, email, date, subject, body = fields
    message = body.strip()
    note = f"Supernova {sha[:7]}."
    sys.stdout.write(
        f"From {sha} Mon Sep 17 00:00:00 2001\nFrom: {name} <{email}>\nDate: {date}\n"
        f"Subject: [PATCH] {subject_of(subject)}\n\n"
        + (message + "\n\n" if message else "") + note + "\n---\n" + diff + "-- \n"
    )


def main():
    args = sys.argv[1:]
    if args == ["--text"]:
        sys.stdout.write(rename(sys.stdin.read()))
    elif len(args) == 1 and not args[0].startswith("-"):
        port(args[0])
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
