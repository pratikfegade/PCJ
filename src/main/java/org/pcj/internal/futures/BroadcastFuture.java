/*
 * Copyright (c) 2011-2018, PCJ Library, Marek Nowicki
 * All rights reserved.
 *
 * Licensed under New BSD License (3-clause license).
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package org.pcj.internal.futures;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.pcj.PcjFuture;
import org.pcj.PcjRuntimeException;

public class BroadcastFuture extends InternalFuture<Void> implements PcjFuture<Void> {
    private PcjRuntimeException exception;

    protected BroadcastFuture() {
    }

    @Override
    protected void signalDone() {
        super.signalDone();
    }

    protected void signalException(PcjRuntimeException exception) {
        this.exception = exception;
        super.signalDone();
    }

    @Override
    public boolean isDone() {
        return super.isSignaled();
    }

    @Override
    public Void get() throws PcjRuntimeException {
        try {
            super.await();
        } catch (InterruptedException ex) {
            throw new PcjRuntimeException(ex);
        }
        if (exception != null) {
            throw exception;
        }
        return null;
    }

    @Override
    public Void get(long timeout, TimeUnit unit) throws TimeoutException, PcjRuntimeException {
        try {
            super.await(timeout, unit);
        } catch (InterruptedException ex) {
            throw new PcjRuntimeException(ex);
        }
        if (exception != null) {
            throw exception;
        }
        return null;
    }
}
