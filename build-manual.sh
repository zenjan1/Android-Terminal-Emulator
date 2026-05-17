#!/bin/bash
set -e

PROJ="/data/data/com.termux/files/home/tmp-work/android-terminal"
cd "$PROJ"

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
AAPT2="/data/data/com.termux/files/usr/bin/aapt2"
D8="$ANDROID_HOME/build-tools/34.0.0/d8"
ZIPALIGN="/data/data/com.termux/files/usr/bin/zipalign"
APKSIGNER="/data/data/com.termux/files/usr/bin/apksigner"

BUILD_DIR="build-manual"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/{R-out,classes,aidl,res-compiled}"

echo "=== Step 1: Compile resources with aapt2 ==="

# Compile each resource file individually
for res_dir in term/src/main/res emulatorview/src/main/res libtermexec/src/main/res; do
    if [ -d "$res_dir" ]; then
        echo "  Compiling $res_dir..."
        for f in $(find "$res_dir" -type f); do
            $AAPT2 compile "$f" -o "$BUILD_DIR/res-compiled" 2>/dev/null || true
        done
    fi
done

FLAT_COUNT=$(find "$BUILD_DIR/res-compiled" -name '*.flat' | wc -l)
COMPILED_RES=$(find "$BUILD_DIR/res-compiled" -name '*.flat' | tr '\n' ' ')
echo "  Found $FLAT_COUNT compiled resource files"

echo "=== Step 2: Link resources ==="

# Use aapt2 link to create APK and generate R.java
$AAPT2 link \
    -I "$ANDROID_JAR" \
    --manifest term/src/main/AndroidManifest.xml \
    $COMPILED_RES \
    -o "$BUILD_DIR/resources.apk" \
    --java "$BUILD_DIR/R-out" \
    --auto-add-overlay \
    -v

echo "  -> resources.apk and R.java created"

echo "=== Step 3: Compile Java sources ==="

# Collect all Java files
JAVA_FILES=""
for module in term emulatorview libtermexec; do
    if [ -d "$module/src/main/java" ]; then
        for f in $(find "$module/src/main/java" -name '*.java'); do
            JAVA_FILES="$JAVA_FILES $f"
        done
    fi
done
# Add generated R.java
for f in $(find "$BUILD_DIR/R-out" -name '*.java' 2>/dev/null); do
    JAVA_FILES="$JAVA_FILES $f"
done
# Add AIDL generated Java
for f in $(find "$BUILD_DIR/aidl" -name '*.java' 2>/dev/null); do
    JAVA_FILES="$JAVA_FILES $f"
done

FILE_COUNT=$(echo $JAVA_FILES | wc -w)
echo "  Compiling $FILE_COUNT Java files..."

# Find annotation jar
ANNOTATION_JAR=""
for jar in $(find $HOME/.gradle/caches -name 'annotation-1*.jar' 2>/dev/null); do
    ANNOTATION_JAR="$jar"
    break
done

CP=""
if [ -n "$ANNOTATION_JAR" ]; then
    CP="$ANNOTATION_JAR"
fi

javac -d "$BUILD_DIR/classes" \
    -bootclasspath "$ANDROID_JAR" \
    -source 11 -target 11 \
    -encoding UTF-8 \
    ${CP:+-classpath "$CP"} \
    -Xlint:none \
    $JAVA_FILES 2>&1

echo "  -> Java compiled successfully"

echo "=== Step 4: Convert to DEX ==="

$d8 --release --output "$BUILD_DIR" "$BUILD_DIR/classes" ${CP:+"$CP"}

echo "  -> classes.dex created"

echo "=== Step 5: Assemble APK ==="

APK_UNSIGNED="$BUILD_DIR/app-unsigned.apk"
APK_ALIGNED="$BUILD_DIR/app-aligned.apk"
APK_FINAL="$BUILD_DIR/app-debug.apk"

# Copy the resource APK as base
cp "$BUILD_DIR/resources.apk" "$APK_UNSIGNED"

# Add classes.dex
cd "$BUILD_DIR"
if [ -f classes.dex ]; then
    zip "$APK_UNSIGNED" classes.dex > /dev/null 2>&1
    echo "  -> classes.dex added"
else
    echo "ERROR: classes.dex not found!"
    ls -la *.dex 2>/dev/null || echo "No .dex files"
    exit 1
fi
cd "$PROJ"

echo "=== Step 6: Zipalign ==="

$ZIPALIGN -f -p 4 "$APK_UNSIGNED" "$APK_ALIGNED"
echo "  -> APK aligned"

echo "=== Step 7: Sign APK ==="

DEBUG_KEYSTORE="$HOME/.android/debug.keystore"
if [ ! -f "$DEBUG_KEYSTORE" ]; then
    mkdir -p "$HOME/.android"
    keytool -genkey -v -keystore "$DEBUG_KEYSTORE" \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
fi

$APKSIGNER sign --ks "$DEBUG_KEYSTORE" \
    --ks-pass pass:android \
    --out "$APK_FINAL" \
    "$APK_ALIGNED"

echo "  -> APK signed"

echo ""
echo "=== BUILD SUCCESS ==="
echo "APK: $APK_FINAL"
ls -lh "$APK_FINAL"
