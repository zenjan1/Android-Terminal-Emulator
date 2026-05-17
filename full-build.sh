#!/bin/bash
set -e
cd /data/data/com.termux/files/home/tmp-work/android-terminal

ANDROID_HOME="$HOME/Android/Sdk"
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
AAPT2="/data/data/com.termux/files/usr/bin/aapt2"
D8="$ANDROID_HOME/build-tools/34.0.0/d8"
ZIPALIGN="/data/data/com.termux/files/usr/bin/zipalign"
APKSIGNER="/data/data/com.termux/files/usr/bin/apksigner"

B="build-manual"
rm -rf "$B"
mkdir -p "$B/R-out" "$B/classes" "$B/aidl/jackpal/androidterm/libtermexec/v1" "$B/res-compiled" "$B/libs"

# Create ITerminal stub (from libtermexec AIDL)
cat > "$B/aidl/jackpal/androidterm/libtermexec/v1/ITerminal.java" << 'AIDL_EOF'
package jackpal.androidterm.libtermexec.v1;

import android.content.IntentSender;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ResultReceiver;

public interface ITerminal extends IInterface {
    IntentSender startSession(ParcelFileDescriptor pseudoTerminalMultiplexerFd, ResultReceiver callback) throws RemoteException;

    abstract class Stub extends android.os.Binder implements ITerminal {
        private static final java.lang.String DESCRIPTOR = "jackpal.androidterm.libtermexec.v1.ITerminal";
        static final int TRANSACTION_startSession = android.os.IBinder.FIRST_CALL_TRANSACTION + 0;

        public Stub() { this.attachInterface(this, DESCRIPTOR); }

        public static ITerminal asInterface(IBinder obj) {
            if (obj == null) return null;
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof ITerminal) return (ITerminal) iin;
            return new Proxy(obj);
        }

        @Override public IBinder asBinder() { return this; }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION:
                    reply.writeString(DESCRIPTOR);
                    return true;
                case TRANSACTION_startSession:
                    data.enforceInterface(DESCRIPTOR);
                    ParcelFileDescriptor fd = data.readInt() != 0 ? ParcelFileDescriptor.CREATOR.createFromParcel(data) : null;
                    ResultReceiver callback = data.readInt() != 0 ? ResultReceiver.CREATOR.createFromParcel(data) : null;
                    IntentSender result = this.startSession(fd, callback);
                    reply.writeNoException();
                    if (result != null) { reply.writeInt(1); result.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE); }
                    else { reply.writeInt(0); }
                    return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements ITerminal {
            private IBinder mRemote;
            Proxy(IBinder remote) { mRemote = remote; }
            @Override public IBinder asBinder() { return mRemote; }
            public java.lang.String getInterfaceDescriptor() { return DESCRIPTOR; }

            @Override
            public IntentSender startSession(ParcelFileDescriptor fd, ResultReceiver callback) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                IntentSender result;
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (fd != null) { data.writeInt(1); fd.writeToParcel(data, 0); } else { data.writeInt(0); }
                    if (callback != null) { data.writeInt(1); callback.writeToParcel(data, 0); } else { data.writeInt(0); }
                    mRemote.transact(Stub.TRANSACTION_startSession, data, reply, 0);
                    reply.readException();
                    result = reply.readInt() != 0 ? IntentSender.CREATOR.createFromParcel(reply) : null;
                } finally { reply.recycle(); data.recycle(); }
                return result;
            }
        }
    }
}
AIDL_EOF

echo "=== 1. Extract AAR libs ==="
for aar in $(find $HOME/.gradle/caches -name '*.aar' 2>/dev/null | grep modules); do
    name=$(basename "$aar" | sed 's/\.[^.]*$//')
    unzip -o "$aar" "classes.jar" -d "$B/libs/$name" > /dev/null 2>&1 || true
done
# Also add annotation jar
mkdir -p "$B/libs/annotation-1.8.0"
cp $HOME/.gradle/caches/modules-2/files-2.1/androidx.annotation/annotation-jvm/1.8.0/b8a16fe526014b7941c1debaccaf9c5153692dbb/annotation-jvm-1.8.0.jar "$B/libs/annotation-1.8.0/classes.jar"
echo "  $(find $B/libs -name 'classes.jar' | wc -l) jars extracted"

echo "=== 2. Compile resources ==="
for res_dir in term/src/main/res emulatorview/src/main/res libtermexec/src/main/res; do
    if [ -d "$res_dir" ]; then
        for f in $(find "$res_dir" -type f); do
            $AAPT2 compile "$f" -o "$B/res-compiled" 2>/dev/null || true
        done
    fi
done
echo "  $(find $B/res-compiled -name '*.flat' | wc -l) flat files"

echo "=== 3. Link resources + generate R.java ==="
COMPILED_RES=$(find "$B/res-compiled" -name '*.flat' | tr '\n' ' ')
$AAPT2 link \
    -I "$ANDROID_JAR" \
    --manifest term/src/main/AndroidManifest.xml \
    $COMPILED_RES \
    -o "$B/resources.apk" \
    --java "$B/R-out" \
    --auto-add-overlay \
    2>&1 | tail -3
echo "  resources.apk + R.java created"

echo "=== 4. Compile Java ==="
JAVA_FILES=$(find term/src/main/java emulatorview/src/main/java libtermexec/src/main/java "$B/R-out" "$B/aidl" -name '*.java' 2>/dev/null)
FILE_COUNT=$(echo "$JAVA_FILES" | wc -l)
echo "  $FILE_COUNT source files"

# Build classpath
CP=""
for jar in $(find "$B/libs" -name 'classes.jar'); do
    CP="${CP:+$CP:}$jar"
done
CP="${CP:+$CP:}$ANDROID_JAR"

javac -d "$B/classes" \
    -classpath "$CP" \
    -source 1.8 -target 1.8 \
    -encoding UTF-8 \
    -Xlint:none \
    $JAVA_FILES 2>&1 | tail -3
echo "  Java compiled"

echo "=== 5. Merge AAR jars with project classes ==="
# Copy all AAR classes.jar files into our classes dir
for jar in $(find "$B/libs" -name 'classes.jar'); do
    cd "$(dirname "$jar")"
    jar xf classes.jar -d "$B/classes" 2>/dev/null || true
    cd - > /dev/null
done

cd "$B/classes"
jar cf ../classes.jar .
cd - > /dev/null
echo "  classes.jar: $(ls -lh "$B/classes.jar" | awk '{print $5}')"

echo "=== 6. Convert to DEX ==="
dx --dex --output="$B/classes.dex" "$B/classes.jar" 2>&1 | tail -3
echo "  DEX: $(ls -lh $B/classes.dex 2>/dev/null | awk '{print $5}')"

echo "=== 6b. Compile native libraries ==="
CLANG="/data/data/com.termux/files/usr/bin/aarch64-linux-android-clang++"
mkdir -p "$B/lib/arm64-v8a"
if command -v "$CLANG" > /dev/null 2>&1; then
    # libjackpal-termexec2.so
    if [ -f libtermexec/src/main/cpp/process.cpp ]; then
        $CLANG -shared -fPIC -O2 \
            -o "$B/lib/arm64-v8a/libjackpal-termexec2.so" \
            libtermexec/src/main/cpp/process.cpp \
            -landroid -llog
        echo "  libjackpal-termexec2.so compiled"
    fi
    # libjackpal-androidterm5.so
    if [ -f term/src/main/cpp/termExec.cpp ]; then
        $CLANG -shared -fPIC -O2 \
            -o "$B/lib/arm64-v8a/libjackpal-androidterm5.so" \
            term/src/main/cpp/termExec.cpp term/src/main/cpp/common.cpp \
            -landroid -llog
        echo "  libjackpal-androidterm5.so compiled"
    fi
    # Bundle libc++_shared.so
    NDK_LIBCXX="$HOME/Android/Sdk/ndk/26.1.10909125/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
    if [ -f "$NDK_LIBCXX" ]; then
        cp "$NDK_LIBCXX" "$B/lib/arm64-v8a/libc++_shared.so"
        echo "  libc++_shared.so bundled"
    fi
else
    echo "  WARNING: clang++ not found, native libs not compiled"
fi

echo "=== 7. Assemble APK ==="
cp "$B/resources.apk" "$B/app-unsigned.apk"
cd "$B"
if [ -f classes.dex ]; then
    zip app-unsigned.apk classes.dex > /dev/null
    echo "  classes.dex added"
fi
# Add native libraries
if [ -d lib/arm64-v8a ]; then
    zip -r app-unsigned.apk lib/ > /dev/null
    echo "  native libs: $(find lib/arm64-v8a -name '*.so' | tr '\n' ' ')"
fi
cd - > /dev/null

echo "=== 8. Zipalign ==="
$ZIPALIGN -f -p 4 "$B/app-unsigned.apk" "$B/app-aligned.apk"

echo "=== 9. Sign ==="
KEYSTORE="$HOME/.android/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$HOME/.android"
    keytool -genkey -v -keystore "$KEYSTORE" \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
fi
$APKSIGNER sign --ks "$KEYSTORE" --ks-pass pass:android \
    --out "$B/app-debug.apk" "$B/app-aligned.apk"

echo ""
echo "=== BUILD COMPLETE ==="
ls -lh "$B/app-debug.apk"
$AAPT2 dump badging "$B/app-debug.apk" 2>/dev/null | head -3 || \
    /data/data/com.termux/files/usr/bin/aapt dump badging "$B/app-debug.apk" 2>/dev/null | head -3
