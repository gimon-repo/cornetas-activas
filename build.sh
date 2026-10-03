#!/bin/sh
# Compila el APK sin Gradle. Requiere: aapt2, android.jar (API 34), dx (dalvik-dx), apksigner.jar y un JDK.
# Variables: AAPT2, ANDROID_JAR, DX_JAR, APKSIGNER_JAR, KEYSTORE
set -e
cd "$(dirname "$0")"
OUT=build
rm -rf $OUT && mkdir -p $OUT/classes $OUT/gen $OUT/dex
"$AAPT2" compile --dir app/res -o $OUT/res.zip
"$AAPT2" link -I "$ANDROID_JAR" --manifest app/AndroidManifest.xml \
  --java $OUT/gen -o $OUT/unsigned.apk $OUT/res.zip --min-sdk-version 21 --target-sdk-version 34
javac --release 8 -cp "$ANDROID_JAR" -nowarn -d $OUT/classes \
  $(find app/src $OUT/gen -name '*.java')
java -cp "$DX_JAR" com.android.dx.command.Main --dex --min-sdk-version=21 --output=$OUT/dex/classes.dex $OUT/classes
(cd $OUT/dex && zip -q ../unsigned.apk classes.dex)
python3 align.py $OUT/unsigned.apk $OUT/aligned.apk
java -jar "$APKSIGNER_JAR" sign --ks "$KEYSTORE" --ks-pass pass:android --out CornetasActivas.apk $OUT/aligned.apk
java -jar "$APKSIGNER_JAR" verify -v CornetasActivas.apk
