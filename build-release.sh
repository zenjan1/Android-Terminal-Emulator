#!/bin/bash

PROJ="/data/data/com.termux/files/home/projects/Android-Terminal-Emulator"
cd "$PROJ"

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
AAPT2="/data/data/com.termux/files/usr/bin/aapt2"
D8="$ANDROID_HOME/build-tools/34.0.0/d8"
ZIPALIGN="/data/data/com.termux/files/usr/bin/zipalign"
APKSIGNER="/data/data/com.termux/files/usr/bin/apksigner"

BUILD_DIR="build-release"

echo "=== Cleaning build directory ==="
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/merged-res"

echo "=== Step 1: Merge all resources ==="
for res_dir in term/src/main/res emulatorview/src/main/res libtermexec/src/main/res; do
    if [ -d "$res_dir" ]; then
        echo "  Merging $res_dir..."
        find "$res_dir" -mindepth 1 -maxdepth 1 -exec cp -rt "$BUILD_DIR/merged-res" {} + 2>/dev/null || true
    fi
done
MERGED_COUNT=$(find "$BUILD_DIR/merged-res" -type f | wc -l)
echo "  Merged $MERGED_COUNT resource files"

echo "=== Step 2: Compile resources ==="
$AAPT2 compile --dir "$BUILD_DIR/merged-res" -o "$BUILD_DIR/compiled.res.zip"
cd "$BUILD_DIR"
unzip -o compiled.res.zip -d res-flat > /dev/null 2>&1
cd "$PROJ"
FLAT_COUNT=$(find "$BUILD_DIR/res-flat" -name '*.flat' | wc -l)
echo "  Compiled $FLAT_COUNT resource files"

echo "=== Step 3: Link resources ==="
FLAT_LIST=$(find "$BUILD_DIR/res-flat" -name '*.flat' | tr '\n' ' ')
$AAPT2 link \
    -I "$ANDROID_JAR" \
    --manifest term/src/main/AndroidManifest.xml \
    $FLAT_LIST \
    -o "$BUILD_DIR/resources.apk" \
    --java "$BUILD_DIR/R-out" \
    --auto-add-overlay \
    -v 2>&1 | grep -E "error:|warn:|writing R" || true

if [ ! -f "$BUILD_DIR/resources.apk" ]; then
    echo "ERROR: Resource linking failed!"
    exit 1
fi
echo "  -> resources.apk created"

echo "=== Step 3b: AIDL (pre-generated stubs in source tree) ==="
echo "  Using pre-generated ITerminal.Stub from libtermexec/src/main/java"

echo "=== Step 4: Compile Java sources ==="
JAVA_FILES=""
for module in term emulatorview libtermexec; do
    if [ -d "$module/src/main/java" ]; then
        while IFS= read -r f; do
            JAVA_FILES="$JAVA_FILES \"$f\""
        done < <(find "$module/src/main/java" -name '*.java')
    fi
done
while IFS= read -r f; do
    [ -n "$f" ] && JAVA_FILES="$JAVA_FILES \"$f\""
done < <(find "$BUILD_DIR/R-out" -name '*.java' 2>/dev/null)

FILE_COUNT=$(echo "$JAVA_FILES" | wc -w)
echo "  Compiling $FILE_COUNT Java files..."

ANNOTATION_JAR=""
for jar in $(find $HOME/.gradle/caches -name 'annotation-jvm-*.jar' 2>/dev/null); do
    ANNOTATION_JAR="$jar"
    break
done
if [ -z "$ANNOTATION_JAR" ]; then
    for jar in $(find $HOME/.gradle/caches -name 'annotation-1*.jar' 2>/dev/null); do
        ANNOTATION_JAR="$jar"
        break
    done
fi

CP="$ANDROID_JAR"
if [ -n "$ANNOTATION_JAR" ]; then
    CP="$CP:$ANNOTATION_JAR"
    echo "  Using annotation: $(basename $ANNOTATION_JAR)"
fi

eval javac -d "$BUILD_DIR/classes" \
    -classpath "\"$CP\"" \
    -encoding UTF-8 \
    -Xlint:none \
    -parameters \
    $JAVA_FILES 2>&1 | tail -10

if [ $(find "$BUILD_DIR/classes" -name '*.class' | wc -l) -eq 0 ]; then
    echo "ERROR: Java compilation failed!"
    exit 1
fi
echo "  -> Java compiled successfully"

echo "=== Step 5: Convert to DEX ==="
D8_JAR="$ANDROID_HOME/build-tools/34.0.0/lib/d8.jar"
D8_CP="$CP"
if [ -z "$D8_CP" ]; then
    D8_CP="$ANDROID_JAR"
fi

# Collect all class files
CLASS_FILES=$(find "$BUILD_DIR/classes" -name '*.class' | tr '\n' ' ')
CLASS_COUNT=$(echo "$CLASS_FILES" | wc -w)
echo "  Converting $CLASS_COUNT class files to DEX..."

java -cp "$D8_JAR" com.android.tools.r8.D8 \
    --release --output "$BUILD_DIR" \
    --classpath "$D8_CP" \
    $CLASS_FILES 2>&1 | tail -5
if [ ! -f "$BUILD_DIR/classes.dex" ]; then
    echo "ERROR: D8 conversion failed!"
    exit 1
fi
echo "  -> classes.dex created"

echo "=== Step 6: Assemble APK ==="
APK_UNSIGNED="$BUILD_DIR/app-unsigned.apk"
APK_ALIGNED="$BUILD_DIR/app-aligned.apk"
APK_FINAL="$BUILD_DIR/TerminalEmulator-v1.0.71-release.apk"

cp "$BUILD_DIR/resources.apk" "$APK_UNSIGNED"
cd "$BUILD_DIR"
zip "$APK_UNSIGNED" classes.dex > /dev/null 2>&1
cd "$PROJ"
echo "  -> APK assembled"

echo "=== Step 7: Zipalign ==="
$ZIPALIGN -f -p 4 "$APK_UNSIGNED" "$APK_ALIGNED"
echo "  -> APK aligned"

echo "=== Step 8: Sign APK (Release) ==="
RELEASE_KEYSTORE="$HOME/.android/release.keystore"

if [ ! -f "$RELEASE_KEYSTORE" ]; then
    echo "  Creating new release keystore..."
    mkdir -p "$HOME/.android"
    keytool -genkeypair -v \
        -keystore "$RELEASE_KEYSTORE" \
        -alias terminal-release \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass term1nal2024 \
        -keypass term1nal2024 \
        -dname "CN=Zenjan, O=Zenjan Studio, C=KR" 2>&1 | tail -3
    echo "  Keystore: $RELEASE_KEYSTORE"
    echo "  Password: term1nal2024"
fi

$APKSIGNER sign --ks "$RELEASE_KEYSTORE" \
    --ks-pass pass:term1nal2024 \
    --key-pass pass:term1nal2024 \
    --out "$APK_FINAL" \
    "$APK_ALIGNED"

echo "  -> APK signed"

echo ""
echo "=== Verify signature ==="
$APKSIGNER verify --verbose "$APK_FINAL"

echo ""
echo "========================================"
echo "  BUILD SUCCESS"
echo "========================================"
echo "APK: $APK_FINAL"
ls -lh "$APK_FINAL"
echo ""
echo "Install:  adb install -r $APK_FINAL"
echo "Reinstall: adb install -r -d $APK_FINAL"
