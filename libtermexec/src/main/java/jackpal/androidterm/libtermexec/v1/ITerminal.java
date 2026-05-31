package jackpal.androidterm.libtermexec.v1;

import android.content.IntentSender;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ResultReceiver;

public interface ITerminal extends IInterface {
    IntentSender startSession(ParcelFileDescriptor pseudoTerminalMultiplexerFd, ResultReceiver callback) throws RemoteException;

    abstract class Stub extends android.os.Binder implements ITerminal {
        private static final String DESCRIPTOR = "jackpal.androidterm.libtermexec.v1.ITerminal";
        static final int TRANSACTION_startSession = IBinder.FIRST_CALL_TRANSACTION;

        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        public static ITerminal asInterface(IBinder obj) {
            if (obj == null) return null;
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof ITerminal) return (ITerminal) iin;
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() { return this; }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == TRANSACTION_startSession) {
                data.enforceInterface(DESCRIPTOR);
                ParcelFileDescriptor fd = data.readInt() != 0 ? ParcelFileDescriptor.CREATOR.createFromParcel(data) : null;
                ResultReceiver callback = data.readInt() != 0 ? ResultReceiver.CREATOR.createFromParcel(data) : null;
                IntentSender result = startSession(fd, callback);
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
            @Override
            public IntentSender startSession(ParcelFileDescriptor fd, ResultReceiver callback) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    if (fd != null) { data.writeInt(1); fd.writeToParcel(data, 0); } else { data.writeInt(0); }
                    if (callback != null) { data.writeInt(1); callback.writeToParcel(data, 0); } else { data.writeInt(0); }
                    mRemote.transact(TRANSACTION_startSession, data, reply, 0);
                    reply.readException();
                    IntentSender result = reply.readInt() != 0 ? IntentSender.CREATOR.createFromParcel(reply) : null;
                    return result;
                } finally { reply.recycle(); data.recycle(); }
            }
        }
    }
}
