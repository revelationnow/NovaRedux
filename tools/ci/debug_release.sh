#!/usr/bin/env bash
# Puts this build's debug APKs on the draft release that always holds the
# newest debug build of main, replacing the ones there (the release is made
# on the first run). A draft is seen only by people who can push to the
# repository and makes no tag, so nothing is published; its asset links stay
# the same from build to build, and install straight from a phone signed in
# to GitHub.
#
# Usage: tools/ci/debug_release.sh <NovaRedux debug APK> <Connect debug APK>
# Needs GH_TOKEN (contents: write), GH_REPO and GITHUB_SHA.
set -euo pipefail

tag=debug-build
title="Debug build (newest main)"
app_apk=$1
connect_apk=$2

out=$(mktemp -d)
cp "$app_apk" "$out/novaredux-debug.apk"
cp "$connect_apk" "$out/novaredux-connect-debug.apk"

# The signing certificate's digest, so a change of debug key shows on the
# release. Best effort: the APKs go up without it.
cert_digest() {
    local tools
    tools=$(ls -d "${ANDROID_HOME:-/nonexistent}"/build-tools/*/ 2>/dev/null | sort -V | tail -1)
    [ -n "$tools" ] || return 0
    "${tools}apksigner" verify --print-certs "$1" 2>/dev/null | sed -n 's/.*certificate SHA-256 digest: //p' | head -1
}
cert=$(cert_digest "$out/novaredux-debug.apk" || true)

notes="$out/notes.md"
cat > "$notes" <<EOF
The newest debug build of main: ${GITHUB_SHA:0:7}, $(date -u '+%-d %b %Y, %H:%M UTC').

- novaredux-debug.apk: NovaRedux Debug, which installs beside a release build.
- novaredux-connect-debug.apk: NovaRedux Connect Debug, for weather and web suggestions.

Every push to main replaces both, under the same links. This draft is seen only by people who can push to the repository, and stays a draft: it is never published.

Debug signing certificate (SHA-256): ${cert:-not read}. CI keeps the debug key in its cache, which GitHub clears after a week with no build of main; when that happens Android refuses the update, and the debug apps have to be uninstalled before the new ones install.
EOF

# Found by its title: GitHub gives an edited draft an untagged-... tag name
# of its own, so the tag it was made with can't find it again. A second
# draft of the same title, from a run that missed the first, goes; the
# oldest keeps its links.
ids=$(gh api "repos/$GH_REPO/releases?per_page=100" \
    --jq "[.[] | select(.draft and .name == \"$title\") | .id] | sort | .[]")
id=$(echo "$ids" | head -n 1)
for extra in $(echo "$ids" | tail -n +2); do
    gh api -X DELETE "repos/$GH_REPO/releases/$extra"
done
if [ -z "$id" ]; then
    id=$(gh api -X POST "repos/$GH_REPO/releases" \
        -f tag_name="$tag" -f target_commitish="$GITHUB_SHA" -f name="$title" \
        -F draft=true -F prerelease=true -F body=@"$notes" --jq .id)
else
    gh api -X PATCH "repos/$GH_REPO/releases/$id" \
        -f target_commitish="$GITHUB_SHA" -F body=@"$notes" > /dev/null
fi

for name in novaredux-debug.apk novaredux-connect-debug.apk; do
    for asset in $(gh api "repos/$GH_REPO/releases/$id/assets" --jq ".[] | select(.name == \"$name\") | .id"); do
        gh api -X DELETE "repos/$GH_REPO/releases/assets/$asset"
    done
    gh api -X POST "https://uploads.github.com/repos/$GH_REPO/releases/$id/assets?name=$name" \
        -H "Content-Type: application/vnd.android.package-archive" --input "$out/$name" > /dev/null
done

gh api "repos/$GH_REPO/releases/$id" --jq '.html_url, (.assets[] | .browser_download_url)'
