package jp.tkgktyk.wearablepadlib;

import android.os.Parcel;
import android.os.Parcelable;

import java.nio.ByteBuffer;

/**
 * Created by tkgktyk on 2015/05/01.
 */
public class TouchMessage implements Parcelable {
    public static final byte EVENT_UNKNOWN = (byte) 0x00;

    public static final byte EVENT_DOWN = (byte) 0x01;
    public static final byte EVENT_UP = (byte) 0x02;
    public static final byte EVENT_MOVE = (byte) 0x03;
    public static final byte EVENT_START_DRAG = (byte) 0x04;

    public static final byte EVENT_SINGLE_TAP = (byte) 0x10;
    public static final byte EVENT_DOUBLE_TAP = (byte) 0x20;
    public static final byte EVENT_BACK = (byte) 0x30;
    public static final byte EVENT_TASKS = (byte) 0x40;
    public static final byte EVENT_HOME = (byte) 0x50;
    public static final byte EVENT_EXIT = (byte) 0x60;

    public byte event;
    public short x;
    public short y;

    public static final Creator<TouchMessage> CREATOR = new Creator<TouchMessage>() {
        @Override
        public TouchMessage createFromParcel(Parcel source) {
            return new TouchMessage(source);
        }

        @Override
        public TouchMessage[] newArray(int size) {
            return new TouchMessage[size];
        }
    };

    private TouchMessage(Parcel source) {
        event = source.readByte();
        x = readShort(source);
        y = readShort(source);
    }

    public TouchMessage() {
    }

    private short readShort(Parcel source) {
        final byte[] buf = new byte[Short.SIZE / Byte.SIZE];
        source.readByteArray(buf);
        return ByteBuffer.wrap(buf).getShort();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(event);
        dest.writeByteArray(ByteBuffer.allocate(Short.SIZE / Byte.SIZE).putShort(x).array());
        dest.writeByteArray(ByteBuffer.allocate(Short.SIZE / Byte.SIZE).putShort(y).array());
    }
}
