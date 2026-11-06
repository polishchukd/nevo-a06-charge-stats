#!/usr/bin/env bash
# ChargeStats C1 build: aapt2 + javac + d8 + zipalign + apksigner (platform key).
set -euo pipefail
cd "$(dirname "$0")"

SDK=${ANDROID_HOME:-/opt/android-sdk}
BT=$SDK/build-tools/34.0.0
PLATFORM=$SDK/platforms/android-34/android.jar
OUT=out
mkdir -p "$OUT/obj" "$OUT/classes" "$OUT/gen"

echo "[1/5] aapt2 compile + link"
rm -rf "$OUT/res" && mkdir -p "$OUT/res"
"$BT/aapt2" compile --dir res -o "$OUT/res/res.zip"
"$BT/aapt2" link -o "$OUT/app.unaligned.apk" -I "$PLATFORM" \
    --manifest AndroidManifest.xml --auto-add-overlay "$OUT/res/res.zip"

echo "[2/5] javac"
find src -name '*.java' > "$OUT/srcs.txt"
javac -nowarn -source 8 -target 8 -bootclasspath "$PLATFORM" \
      -d "$OUT/classes" @"$OUT/srcs.txt"

echo "[3/5] d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --release --min-api 29 --lib "$PLATFORM" \
      --output "$OUT/classes" $(cat "$OUT/classes.txt") >/dev/null

echo "[4/5] package + align"
cp "$OUT/app.unaligned.apk" "$OUT/app.aligned.apk"
(cd "$OUT/classes" && zip -q "$OLDPWD/$OUT/app.aligned.apk" classes.dex)
"$BT/zipalign" -f 4 "$OUT/app.aligned.apk" "$OUT/ChargeStats.unsigned.apk"

echo "[5/5] sign"
if [ -f keys/platform.pk8 ] && [ -f keys/platform.x509.pem ]; then
  "$BT/apksigner" sign --key keys/platform.pk8 --cert keys/platform.x509.pem \
      --out "$OUT/ChargeStats.apk" "$OUT/ChargeStats.unsigned.apk"
  echo "signed with PLATFORM test key"
else
  echo "platform keys missing — copy from charge-probe/keys/"
  exit 1
fi
ls -la "$OUT/ChargeStats.apk"
