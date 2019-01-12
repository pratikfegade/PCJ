/*
 * Copyright (c) 2011-2016, PCJ Library, Marek Nowicki
 * All rights reserved.
 *
 * Licensed under New BSD License (3-clause license).
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package org.pcj.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.pcj.internal.message.Message;
import org.pcj.internal.message.MessageType;
import org.pcj.internal.network.LoopbackMessageBytesStream;
import org.pcj.internal.network.LoopbackSocketChannel;
import org.pcj.internal.network.MessageBytesInputStream;
import org.pcj.internal.network.MessageBytesOutputStream;
import org.pcj.internal.network.MessageDataInputStream;
import org.pcj.internal.network.SelectorProc;

/**
 * This is intermediate class (between classes that want to send data (eg.
 * {@link org.pcj.internal.network.SelectorProc} classes) for sending data
 * across network. It is used for binding address, connecting to hosts and
 * sending data.
 *
 * @author Marek Nowicki (faramir@mat.umk.pl)
 */
final public class Networker {

    private static final Logger LOGGER = Logger.getLogger(Networker.class.getName());
    private final SelectorProc selectorProc;
    private final Thread selectorProcThread;
    private final ExecutorService workers;
    private static AtomicInteger numRemote = new AtomicInteger();

    static {
	Runtime.getRuntime().addShutdownHook(new Thread() {
		public void run() {
		    System.out.println("Number of remote messages " + numRemote.get());
		}
	    });
    }

    protected Networker(int minWorkerCount, int maxWorkerCount) {
        LOGGER.log(Level.FINE, "Networker with {0,number,#}-{1,number,#} {1,choice,1#worker|1<workers}",
                new Object[]{minWorkerCount, maxWorkerCount});

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "Worker-" + counter.getAndIncrement());
            }
        };

        this.workers = new ThreadPoolExecutor(
                minWorkerCount, maxWorkerCount,
                Configuration.WORKERS_KEEPALIVE, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Configuration.WORKERS_QUEUE_SIZE),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy());

        this.selectorProc = new SelectorProc();

        this.selectorProcThread = new Thread(selectorProc, "SelectorProc");
        this.selectorProcThread.setDaemon(true);
    }

    void startup() {
        selectorProcThread.start();

    }

    ServerSocketChannel bind(InetAddress hostAddress, int port, int backlog) throws IOException {
        return selectorProc.bind(hostAddress, port, backlog);
    }

    public SocketChannel connectTo(InetAddress hostAddress, int port) throws IOException, InterruptedException {
        SocketChannel socket = selectorProc.connectTo(hostAddress, port);
        waitForConnectionEstablished(socket);
        return socket;
    }

    private void waitForConnectionEstablished(SocketChannel socket) throws InterruptedException, IOException {
        synchronized (socket) {
            while (socket.isConnected() == false) {
                if (socket.isConnectionPending() == false) {
                    throw new IOException("Unable to connect to " + socket.getRemoteAddress());
                }
                socket.wait(100);
            }
        }
    }

    void shutdown() {
        while (true) {
            try {
                Thread.sleep(10);
                selectorProc.closeAllSockets();
                break;
            } catch (IOException ex) {
                LOGGER.log(Level.FINEST, "Exception while closing sockets: {0}", ex.getMessage());
            } catch (InterruptedException ex) {
                LOGGER.log(Level.FINEST, "Interrupted while shutting down. Force shutdown.");
                break;
            }
        }

        selectorProcThread.interrupt();
        workers.shutdownNow();
    }

    public void send(SocketChannel socket, Message message) {
        try {
            if (socket instanceof LoopbackSocketChannel) {
		if (message.getType() != MessageType.ASYNC_AT_REQUEST && message.getType() != MessageType.ASYNC_AT_RESPONSE) {
		    LoopbackMessageBytesStream loopbackMessageBytesStream = new LoopbackMessageBytesStream(message);
		    loopbackMessageBytesStream.writeMessage();
		    loopbackMessageBytesStream.close();

		    if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.log(Level.FINEST, "Locally processing message {0}", message.getType());
		    }
		    workers.submit(new WorkerTask(socket, message, loopbackMessageBytesStream.getMessageDataInputStream()));
		}
		else {
		    workers.submit(new LocalWorkerTask(socket, message));
		}
            } else {
		numRemote.incrementAndGet();
                MessageBytesOutputStream objectBytes = new MessageBytesOutputStream(message);
                objectBytes.writeMessage();
                objectBytes.close();

                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.log(Level.FINEST, "Sending message {0} to {1}", new Object[]{message.getType(), socket});
                }
                selectorProc.writeMessage(socket, objectBytes);
            }
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Exception while sending message", t);
        }
    }

    public void processMessageBytes(SocketChannel socket, MessageBytesInputStream messageBytes) {
        MessageDataInputStream messageDataInputStream = messageBytes.getMessageDataInputStream();
        Message message;
        try {
            byte messageType = messageDataInputStream.readByte();
            message = MessageType.valueOf(messageType).create();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "Received message {0} from {1}", new Object[]{message.getType(), socket});
        }

        workers.submit(new WorkerTask(socket, message, messageDataInputStream));
    }

    private static class LocalWorkerTask implements Runnable {
        private final SocketChannel socket;
        private final Message message;

        public LocalWorkerTask(SocketChannel socket, Message message) {
            this.socket = socket;
            this.message = message;
        }

        @Override
        public void run() {
            try {
                message.executeLocal(socket);
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Exception while processing message " + message
                        + " by node(" + InternalPCJ.getNodeData().getPhysicalId() + ").", t);
            }
        }
    }

    private static class WorkerTask implements Runnable {
        private final SocketChannel socket;
        private final Message message;
        private final MessageDataInputStream messageDataInputStream;

        public WorkerTask(SocketChannel socket, Message message, MessageDataInputStream messageDataInputStream) {
            this.socket = socket;
            this.message = message;
            this.messageDataInputStream = messageDataInputStream;
        }

        @Override
        public void run() {
            try {
                message.execute(socket, messageDataInputStream);
                messageDataInputStream.close();
            } catch (Throwable t) {
                LOGGER.log(Level.SEVERE, "Exception while processing message " + message
                        + " by node(" + InternalPCJ.getNodeData().getPhysicalId() + ").", t);
            }
        }
    }
}
