#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

OWNER="ctrl-mietze"
REPO="Veyra-Root"
FULL="$OWNER/$REPO"
REL="/data/data/com.termux/files/home/veyra-root-release"
APK="/storage/emulated/0/Download/VeyraRoot-v1.0.0-Official-Hardened-v100000000-signed.apk"
SUMS="/storage/emulated/0/Download/VeyraRoot-v1.0.0-SHA256SUMS.txt"

cd "$REL"

gh auth status -h github.com >/dev/null

if gh repo view "$FULL" >/dev/null 2>&1; then
  echo "ERROR: $FULL already exists; refusing to overwrite an existing repository." >&2
  exit 31
fi

# Requested workflow: create it empty/private first.
gh repo create "$FULL"   --private   --description "Veyra Root — Android root, kernel research, Magic Builder and CVeyra Permission Provider"

echo "EMPTY_PRIVATE_REPO_CREATED=$FULL"

git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/$FULL.git"
git push -u origin main
git push origin v1.0.0

APK_SHA=$(sha256sum "$APK" | awk '{print $1}')
cat > "$SUMS" <<EOF
$APK_SHA  $(basename "$APK")
EOF

gh release create v1.0.0   "$APK#Veyra Root v1.0.0 official hardened APK"   "$SUMS#SHA-256 checksums"   --repo "$FULL"   --verify-tag   --latest   --title "Veyra Root v1.0.0 — First Public Release"   --notes-file "$REL/docs/RELEASE_NOTES_v1.0.0.md"

gh repo edit "$FULL"   --description "Veyra Root — Android root, kernel research, Magic Builder and CVeyra Permission Provider"   --default-branch main   --enable-issues   --enable-wiki=false   --enable-projects=false   --delete-branch-on-merge   --add-topic android   --add-topic root   --add-topic kernelsu   --add-topic veyra   --add-topic kernel-research   --add-topic temp-root   --add-topic magic-builder

# Public is deliberately the LAST action.
gh repo edit "$FULL"   --visibility public   --accept-visibility-change-consequences

echo "PUBLIC_REPO=https://github.com/$FULL"
echo "RELEASE=https://github.com/$FULL/releases/tag/v1.0.0"
echo "APK_SHA256=$APK_SHA"
