/* **
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright 2020, Miguel Arregui a.k.a. marregui
 */
package marregui.logpulse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wrapper class for a fixed thread pool executor with lifecycle:
 * <ol>
 *     <li>instantiate</li>
 *     <li>start</li>
 *     <li>process</li>
 *     <li>process</li>
 *     <li>process...</li>
 *     <li>joinTasks</li>
 *     <li>stop</li>
 * </ol>
 */
public class TaskProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskProcessor.class);

    private final int numThreads;
    private final AtomicInteger threadId;
    private final ThreadFactory threadFactory;
    private final AtomicInteger runningTasksCount;
    private ExecutorService executor;

    /**
     * Constructor.
     * @param numThreads number of threads in the fixed size thread pool
     * @param isDaemon whether the threads are daemons
     */
    public TaskProcessor(int numThreads, boolean isDaemon) {
        this.numThreads = numThreads;
        threadId = new AtomicInteger();
        threadFactory = runnable -> {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setDaemon(isDaemon);
            thread.setName(String.format(
                    "%s%s",
                    TaskProcessor.this.getClass().getSimpleName(),
                    Integer.valueOf(threadId.getAndIncrement())));
            return thread;
        };
        runningTasksCount = new AtomicInteger();
    }

    /**
     * Submits the task to the thread pool executor and manages
     * running task count.
     * @param task to process
     * @return the future representing the task in execution
     */
    public synchronized Future<?> processTask(Runnable task) {
        if (executor == null) {
            throw new IllegalStateException("not running");
        }
        return executor.submit(() -> {
            runningTasksCount.incrementAndGet();
            try {
                task.run();
            } finally {
                runningTasksCount.decrementAndGet();
            }
        });
    }

    /**
     * Starts the thread pool executor making it ready for processing
     * tasks.
     */
    public synchronized void start() {
        if (executor != null) {
            throw new IllegalStateException("already running");
        }
        executor = Executors.newFixedThreadPool(numThreads, threadFactory);
        LOGGER.info("{} Started [numThreads: {}]",
                getClass().getSimpleName(),
                Integer.valueOf(numThreads));
    }

    /**
     * Stops the thread pool executor making it unable to process tasks.
     */
    public synchronized void stop() {
        if (executor == null) {
            throw new IllegalStateException("not running");
        }
        LOGGER.info("{} Stopping", getClass().getSimpleName());
        executor.shutdown();
        try {
            executor.awaitTermination(200L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            executor = null;
            runningTasksCount.set(0);
            LOGGER.info("Stopped");
        }
    }

    /**
     * @return true if the executor is running
     */
    public synchronized boolean isRunning() {
        return executor != null && !executor.isShutdown() && !executor.isTerminated();
    }

    /**
     * @return number of running tasks if the executor is running
     */
    public int getRunningTasksCount() {
        return isRunning() ? runningTasksCount.get() : 0;
    }

    /**
     * Waits for the executor to finish processing tasks. This method may
     * be invoked by another thread to wait on the executor. Return value
     * interpretation.
     * <ul>
     *     <li><b>true</b>: either tasks completed, or timeout expired or the
     *     join operation itself was interrupted.</li>
     *     <li><b>false</b>: the executor is not running.</li>
     * </ul>
     *
     * @param timeout the maximum time to wait in millis
     * @return true on timeout or interrupted, false when executor not running
     */
    public boolean joinTasks(long timeout) {
        try {
            long endTs = timeout > 0 ? System.currentTimeMillis() + timeout : Long.MAX_VALUE;
            long delta = timeout > 0 ? timeout / 4L : 100L;
            while (isRunning() && runningTasksCount.get() > 0) {
                if (System.currentTimeMillis() > endTs) {
                    return true;
                }
                if (timeout >= 0) {
                    TimeUnit.MILLISECONDS.sleep(delta);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        }
        return isRunning();
    }
}
