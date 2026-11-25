#!/usr/bin/env bash
set -eu -o pipefail
cd "$(dirname "$0")"
sdk=${ANDROID_HOME:?Android SDK requerido}
bt="$sdk/build-tools/35.0.0"
api="$sdk/platforms/android-35/android.jar"
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
mkdir -p "$out/classes" "$out/lib/armeabi-v7a"
# A real ARM library selects the 32-bit VM needed by the television middleware.
printf 'int tdt_lab_abi(void) {return 32;}\n' > "$out/abi.c"
clang --target=armv7a-linux-androideabi30 -fuse-ld=lld -nostdlib -shared -fPIC -Wl,-soname,libtdtlab.so "$out/abi.c" -o "$out/lib/armeabi-v7a/libtdtlab.so"
javac --release 8 -classpath "$api" -encoding UTF-8 -d "$out/classes" src/es/ayana/tdtlab/*.java
jar cf "$out/classes.jar" -C "$out/classes" .
"$bt/d8" --lib "$api" --min-api 30 --output "$out" "$out/classes.jar"
"$bt/aapt2" link -I "$api" --manifest AndroidManifest.xml -o "$out/base.apk"
(cd "$out" && zip -q -0 base.apk classes.dex lib/armeabi-v7a/libtdtlab.so)
"$bt/zipalign" -f 4 "$out/base.apk" "$out/aligned.apk"
ks=${LAB_KEYSTORE:-"$out/debug.jks"}
if [[ ! -f "$ks" ]]; then
  keytool -genkeypair -keystore "$ks" -storepass android -keypass android -alias lab -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=TDT Lab" >/dev/null
fi
"$bt/apksigner" sign --ks "$ks" --ks-pass pass:android --out O-TDT-Lab-0.1.0.apk "$out/aligned.apk"
"$bt/apksigner" verify --verbose O-TDT-Lab-0.1.0.apk
"$bt/aapt" dump badging O-TDT-Lab-0.1.0.apk | head -80
