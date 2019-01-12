/*
 * Copyright (c) 2011-2016, PCJ Library, Marek Nowicki
 * All rights reserved.
 *
 * Licensed under New BSD License (3-clause license).
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package org.pcj.internal.network;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;

import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectInputNoShared;

/**
 *
 * @author Marek Nowicki (faramir@mat.umk.pl)
 */
public class MessageDataInputStream extends InputStream {

    final private InputStream input;
    private FSTObjectInput fstObjectInput;

    public MessageDataInputStream(InputStream input) {
        this.input = input;
        this.fstObjectInput = null;
    }

    @Override
    public int read() throws IOException {
        return input.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return input.read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return input.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        if (fstObjectInput != null) {
            fstObjectInput.close();
        }
        else {
	    input.close();
	}
    }

    private int bytesToInt(byte[] bytes) {
        return (((int) (bytes[0] & 0xff) << 24)
                | ((int) (bytes[1] & 0xff) << 16)
                | ((int) (bytes[2] & 0xff) << 8)
                | ((int) (bytes[3] & 0xff)));
    }

    private long bytesToLong(byte[] bytes) {
        return (((long) (bytes[0] & 0xff) << 56)
                | ((long) (bytes[1] & 0xff) << 48)
                | ((long) (bytes[2] & 0xff) << 40)
                | ((long) (bytes[3] & 0xff) << 32)
                | ((long) (bytes[4] & 0xff) << 24)
                | ((long) (bytes[5] & 0xff) << 16)
                | ((long) (bytes[6] & 0xff) << 8)
                | ((long) (bytes[7] & 0xff)));
    }

    public boolean readBoolean() throws IOException {
        return input.read() != 0;
    }

    public byte readByte() throws IOException {
        return (byte) input.read();
    }

    public byte[] readByteArray() throws IOException {
        int length = readInt();
        if (length == -1) {
            return null;
        } else {
            byte[] array = new byte[length];
            input.read(array, 0, array.length);
            return array;
        }
    }

    public double readDouble() throws IOException {
        byte[] bytes = new byte[Double.BYTES];
        input.read(bytes, 0, bytes.length);
        long longBits = bytesToLong(bytes);
        return Double.longBitsToDouble(longBits);
    }

    public double[] readDoubleArray() throws IOException {
        int length = readInt();
        if (length == -1) {
            return null;
        } else {
            double[] array = new double[length];
            for (int i = 0; i < length; ++i) {
                array[i] = readDouble();
            }
            return array;
        }
    }

    public float readFloat() throws IOException {
        byte[] bytes = new byte[Float.BYTES];
        input.read(bytes, 0, bytes.length);
        int intBits = bytesToInt(bytes);
        return Float.intBitsToFloat(intBits);
    }

    public float[] readFloatArray() throws IOException {
        int length = readInt();
        if (length == -1) {
            return null;
        } else {
            float[] array = new float[length];
            for (int i = 0; i < length; ++i) {
                array[i] = readFloat();
            }
            return array;
        }
    }

    public int readInt() throws IOException {
        byte[] bytes = new byte[Integer.BYTES];
        input.read(bytes, 0, bytes.length);
        return bytesToInt(bytes);
    }

    public int[] readIntArray() throws IOException {
        int length = readInt();
        if (length == -1) {
            return null;
        } else {
            int[] array = new int[length];
            for (int i = 0; i < length; ++i) {
                array[i] = readInt();
            }
            return array;
        }
    }

    public long readLong() throws IOException {
        byte[] bytes = new byte[Long.BYTES];
        input.read(bytes, 0, bytes.length);
        return bytesToLong(bytes);
    }

    public long[] readLongArray() throws IOException {
        int length = readInt();
        if (length == -1) {
            return null;
        } else {
            long[] array = new long[length];
            for (int i = 0; i < length; ++i) {
                array[i] = readLong();
            }
            return array;
        }
    }

    public String readString() throws IOException {
        int length = readInt();
        if (length == -1) {
            return null;
        } else {
            byte[] bytes = new byte[length];
            input.read(bytes, 0, bytes.length);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    public Object readObject() throws IOException, ClassNotFoundException {
        if (fstObjectInput == null) {
            fstObjectInput = new FSTObjectInput(input);
        }
//        return fstObjectInput.readObject();
        Object object = fstObjectInput.readObject();
	// fstObjectInput.close();
	return object;
    }
}
