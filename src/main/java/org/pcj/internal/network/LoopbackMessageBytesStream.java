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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.pcj.internal.Configuration;
import org.pcj.internal.message.Message;

/**
 *
 * @author Marek Nowicki (faramir@mat.umk.pl)
 */
public class LoopbackMessageBytesStream implements AutoCloseable {

    private static final ByteBufferPool BYTE_BUFFER_POOL = new ByteBufferPool(Configuration.BUFFER_POOL_SIZE, Configuration.BUFFER_CHUNK_SIZE);
    private final Message message;
    private final Queue<ByteBuffer> queue;
    private final MessageDataOutputStream messageDataOutputStream;

    public LoopbackMessageBytesStream(Message message) {
        this(message, Configuration.BUFFER_CHUNK_SIZE);
    }

    public LoopbackMessageBytesStream(Message message, int chunkSize) {
        this.message = message;

        this.queue = new ConcurrentLinkedQueue<>();
        this.messageDataOutputStream = new MessageDataOutputStream(new LoopbackOutputStream(queue, chunkSize));
    }

    public void writeMessage() throws IOException {
        message.write(messageDataOutputStream);
    }

    @Override
    public void close() throws IOException {
        messageDataOutputStream.close();
    }

    public MessageDataInputStream getMessageDataInputStream() {
        return new MessageDataInputStream(new LoopbackInputStream(queue));
    }

    private static class LoopbackOutputStream extends OutputStream {

        private final int chunkSize;
        private final Queue<ByteBuffer> queue;
        private ByteBuffer currentByteBuffer;

        public LoopbackOutputStream(Queue<ByteBuffer> queue, int chunkSize) {
            this.chunkSize = chunkSize;
            this.queue = queue;

            allocateBuffer(chunkSize);
        }

        private void allocateBuffer(int size) {
            if (size <= chunkSize) {
                currentByteBuffer = BYTE_BUFFER_POOL.take();
            } else {
                currentByteBuffer = ByteBuffer.allocate(size);
            }
        }

        private void flush(int size) {
            if (currentByteBuffer.position() <= 0) {
                return;
            }
            currentByteBuffer.flip();
            queue.offer(currentByteBuffer);

            if (size > 0) {
                allocateBuffer(size);
            }
        }

        @Override
        public void flush() {
            flush(chunkSize);
        }

        @Override
        public void close() throws IOException {
	    // System.err.println(System.identityHashCode(this));
	    // (new IllegalArgumentException()).printStackTrace();
            if (currentByteBuffer.position() > 0) {
                flush(0);
            }
            currentByteBuffer = null;
        }

        public boolean isClosed() {
            return currentByteBuffer == null && queue.isEmpty();
        }

        @Override
        public void write(int b) {
            if (currentByteBuffer.hasRemaining() == false) {
                flush();
            }
            currentByteBuffer.put((byte) b);
        }

        @Override
        public void write(byte[] b) {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            int remaining = currentByteBuffer.remaining();
            if (remaining < len) {
                currentByteBuffer.put(b, off, remaining);
                len -= remaining;
                off += remaining;
                flush(Math.max(chunkSize, len));
            }
            currentByteBuffer.put(b, off, len);
        }
    }

    private static class LoopbackInputStream extends InputStream {

        private final Queue<ByteBuffer> queue;

        public LoopbackInputStream(Queue<ByteBuffer> queue) {
            this.queue = queue;
        }

        @Override
        public void close() {
            ByteBuffer bb;
            while ((bb = queue.poll()) != null) {
                BYTE_BUFFER_POOL.give(bb);
            }
        }

        @Override
        public int read() {
            while (true) {
                ByteBuffer byteBuffer = queue.peek();
                if (byteBuffer == null) {

                    return -1;

                } else if (byteBuffer.hasRemaining() == false) {
                    BYTE_BUFFER_POOL.give(byteBuffer);
                    queue.poll();
                } else {
                    return byteBuffer.get() & 0xFF;
                }
            }
        }

        @Override
        public int read(byte[] b) {
            return read(b, 0, b.length);
        }

        @Override
        public int read(byte[] b, int offset, int length) {
            if (length == 0) {
                return 0;
            }

            int bytesRead = 0;
            while (true) {
                ByteBuffer byteBuffer = queue.peek();
                if (byteBuffer == null) {
                    if (bytesRead == 0) {
                        return -1;
                    } else {
                        return bytesRead;
                    }
                } else {
                    int len = Math.min(byteBuffer.remaining(), length - bytesRead);

                    byteBuffer.get(b, offset, len);

                    bytesRead += len;
                    offset += len;

                    if (byteBuffer.hasRemaining() == false) {
                        BYTE_BUFFER_POOL.give(byteBuffer);
                        queue.poll();
                    }

                    if (bytesRead == length) {
                        return length;
                    }
                }
            }
        }
    }
}
