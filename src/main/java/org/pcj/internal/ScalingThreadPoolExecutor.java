/*
 * Copyright (c) 2011-2016, PCJ Library, Marek Nowicki
 * All rights reserved.
 *
 * Licensed under New BSD License (3-clause license).
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package org.pcj.internal;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.RejectedExecutionHandler;

/**
 * This class represents a queue to be used with an Executor
 * service. The implementation is sourced from the blog post
 * https://github.com/kimchy/kimchy.github.com/blob/master/_posts/2008-11-23-juc-executorservice-gotcha.textile
 *
 * @author Pratik Fegade (ppf@cs.cmu.edu)
 */
class ScalingQueue<E> extends LinkedBlockingQueue<E> {
    /**
     * The executor this Queue belongs to
     */
    private ThreadPoolExecutor executor;

    /**
     * Creates a TaskQueue with a capacity of {@link
     * Integer#MAX_VALUE}.
     */
    public ScalingQueue() {
	super();
    }

    /**
     * Creates a TaskQueue with the given (fixed) capacity.
     *
     * @param capacity the capacity of this queue.
     */
    public ScalingQueue(int capacity) {
	super(capacity);
    }

    /**
     * Sets the executor this queue belongs to.
     */
    public void setThreadPoolExecutor(ThreadPoolExecutor executor) {
	this.executor = executor;
    }

    /**
     * Inserts the specified element at the tail of this queue if
     * there is at least one available thread to run the current
     * task. If all pool threads are actively busy, it rejects the
     * offer.
     *
     * @param o the element to add.
     * @return true if it was possible to add the element to this
     * queue, else false
     * @see ThreadPoolExecutor#execute(Runnable)
     */
    @Override
    public boolean offer(E o) {
	int allWorkingThreads = executor.getActiveCount() + super.size();
	return allWorkingThreads < executor.getPoolSize() && super.offer(o);
    }
}

/**
 * This class is basically a wrapper over
 * java.util.concurrent.ThreadPoolExecutor to explicitly maintain
 * a count of the active threads actively executing tasks. The
 * class also uses the ScalingQueue to make sure new threads are
 * created when there are no more idle threads in the executor,
 * limited by the maxPoolSize. The implementation is sourced from
 * the blog post
 * https://github.com/kimchy/kimchy.github.com/blob/master/_posts/2008-11-23-juc-executorservice-gotcha.textile
 *
 * @author Pratik Fegade (ppf@cs.cmu.edu)
 */
public class ScalingThreadPoolExecutor extends ThreadPoolExecutor {
    /**
     * Number of threads that are actively executing tasks
     */
    private final AtomicInteger activeCount = new AtomicInteger();

    ScalingThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
			      TimeUnit unit, ThreadFactory threadFactory) {
	super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new ScalingQueue<Runnable>(), threadFactory);
	((ScalingQueue<Runnable>)getQueue()).setThreadPoolExecutor(this);
    }
    @Override
    public int getActiveCount() {
	return activeCount.get();
    }
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
	activeCount.incrementAndGet();
    }
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
	activeCount.decrementAndGet();
    }
}
