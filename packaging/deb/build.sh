#!/usr/bin/env bash
# Builds mcrl_<version>_all.deb by hand with ar/tar/gzip, no dpkg-deb required (a .deb is just
# an ar archive of debian-binary + control.tar.gz + data.tar.gz). Run from this directory.
set -euo pipefail

# Jar version (matches an mcrl release tag) vs package version (control's Version:, which can
# get a -N debian_revision bump for packaging-only changes without a new jar release).
JAR_VERSION="1.3.0"
PKG_VERSION="1.3.0-2"
JAR_URL="https://github.com/Sm0keSkreen/mcrl/releases/download/v${JAR_VERSION}/mcrl.jar"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "Fetching mcrl.jar ${JAR_VERSION}..."
curl -fLsS -o "$WORK/mcrl.jar" "$JAR_URL"

mkdir -p "$WORK/control" "$WORK/data/usr/share/mcrl"
cp control "$WORK/control/control"
install -m 755 postinst "$WORK/control/postinst"
cp "$WORK/mcrl.jar" "$WORK/data/usr/share/mcrl/mcrl.jar"
chmod 644 "$WORK/data/usr/share/mcrl/mcrl.jar"

echo "2.0" > "$WORK/debian-binary"

( cd "$WORK/control" && tar --owner=root --group=root -czf ../control.tar.gz ./control ./postinst )
( cd "$WORK/data" && tar --owner=root --group=root -czf ../data.tar.gz ./usr )

OUT="mcrl_${PKG_VERSION}_all.deb"
( cd "$WORK" && ar rc "$OUT" debian-binary control.tar.gz data.tar.gz )
mv "$WORK/$OUT" "$OUT"

echo "Built $OUT"
echo "Install with: sudo apt install ./$OUT   (or: sudo dpkg -i ./$OUT)"
