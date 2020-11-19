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

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Instances of this class use the OS's file watching service to detect
 * changes to a specific file (created, deleted, updated). File events
 * result in file content readout, which is stored in a cache of fully
 * parsed UTC Epoch timestamped lines.
 * <p>
 * PeriodicSchedule instances can be set to access the cache by sliding period,
 * e.g. a 10 second period will allow the schedule to access all lines that
 * were produced during the last 10 seconds leading to the schedule being executed,
 * for every 10 seconds period for which there is data available. If there is no
 * data, the schedule is executed nevertheless (to be aware of the absence of data).
 * <p>
 * The thread model consists of three worker threads:
 * <ul>
 *     <li><b>scheduler</b>: Tracks time in approximately 1 second intervals.
 *     This is not an absolute time, or real time, application, rather it is a soft
 *     real time system with a high level of accuracy. The scheduler will take at
 *     most 1 second to poll for changes in the file and allow an async, concurrent,
 *     thread ('readout') to read/cache any changes. Then schedules are checked
 *     and those that are in schedule, i.e. ready, are handed over to yet another
 *     async, concurrent, thread ('schedules-processor') to access the cache and
 *     execute the schedules.</li>
 *     <li><b>readout</b>: this thread loads and parses lines from the file,
 *     to then store them in the cache. It has the opportunity to do so every
 *     second.</li>
 *     <li><b>schedules-processor</b>: this thread executes the threshold and manages
 *     cache eviction.</li>
 * </ul>
 * <p>
 * The workflow for using an instance of this class:
 * <ol>
 *     <li>Instantiate</li>
 *     <li>Set alerts</li>
 *     <li>Start</li>
 *     <li>Stop OR JoinTasks followed by Stop</li>
 * </ol>
 *
 * @param <T> a class implementing {@link WithUTCTimestamp}
 * @see PeriodicSchedule
 * @see FileReadoutHandler
 * @see SchedulesProcessor
 * @see TaskProcessor
 * @see WithUTCTimestamp
 */
public class Scheduler<T extends WithUTCTimestamp> extends TaskProcessor implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Scheduler.class);
    private static final List<Function<Path, Boolean>> PARENT_FOLDER_ATTRS = Arrays.asList(
            Files::exists, Files::isDirectory, Files::isReadable, Files::isExecutable
    );

    // this is what the WatchService reacts to with a frequency of approx 1 second
    private static final WatchEvent.Kind<?>[] WATCH_EVENTS = {
            StandardWatchEventKinds.ENTRY_CREATE, // file is created
            StandardWatchEventKinds.ENTRY_DELETE, // file is deleted
            StandardWatchEventKinds.ENTRY_MODIFY  // file is modified
    };

    private enum WatchEventKind {
        ENTRY_CREATE, // file is created
        ENTRY_DELETE, // file is deleted
        ENTRY_MODIFY, // file is modified
        UNEXPECTED;   // unexpected event kind

        static WatchEventKind kindOf(String str) {
            try {
                return valueOf(WatchEventKind.class, str);
            } catch (IllegalArgumentException | NullPointerException e) {
                return UNEXPECTED;
            }
        }
    }

    private final FileReadoutHandler<T> fileReadoutHandler;
    private final ReadoutCache<T> readoutCache; // holds event data collected by the fileReadoutHandler
    private final SchedulesProcessor<T> schedulesProcessor;
    private final boolean readFileFromTheStart;
    private WatchService watchService;
    private WatchKey watchedFolderKey;
    private Future<?> schedulerThreadHandle;
    private final AtomicBoolean dataAvailable;

    /**
     * Constructor.
     * <p>
     * Reads the file from its size at the moment of {@linkplain #start()},
     * equivalent to consuming the tail as it grows.
     * <p>
     * Threads are daemons.
     *
     * @param fileReadoutHandler readout handler for the watched file
     */
    public Scheduler(FileReadoutHandler<T> fileReadoutHandler) {
        this(fileReadoutHandler, false, true);
    }

    /**
     * Constructor.
     * <p>
     * Reads the file from its size at the moment of {@linkplain #start()},
     * equivalent to consuming the tail as it grows.
     *
     * @param fileReadoutHandler readout handler for the watched file
     * @param threadsAreDaemons  flag to determine whether the threading model
     *                           uses standard threads, or daemon threads
     */
    public Scheduler(FileReadoutHandler<T> fileReadoutHandler, boolean threadsAreDaemons) {
        this(fileReadoutHandler, false, threadsAreDaemons);
    }

    /**
     * Constructor.
     *
     * @param fileReadoutHandler   readout handler for the watched file
     * @param readFileFromTheStart if false the file is read from the end
     * @param threadsAreDaemons    flag to determine whether the threading model
     *                             uses standard threads, or daemon threads
     */
    public Scheduler(FileReadoutHandler<T> fileReadoutHandler, boolean readFileFromTheStart, boolean threadsAreDaemons) {
        super(2, threadsAreDaemons);
        this.fileReadoutHandler = Objects.requireNonNull(fileReadoutHandler);
        readoutCache = new ReadoutCache<>();
        this.readFileFromTheStart = readFileFromTheStart;
        schedulesProcessor = new SchedulesProcessor<>(1, readoutCache, threadsAreDaemons);
        dataAvailable = new AtomicBoolean();
    }

    /**
     * Sets the periodic schedule so that it is triggered every period seconds,
     * and be given the start and end UTC Epochs defining the period, along
     * with the list of events for that period.
     *
     * @param schedule a schedule
     */
    public void setPeriodicSchedule(PeriodicSchedule<T> schedule) {
        schedulesProcessor.setPeriodicSchedule(schedule);
    }

    /**
     * @return path of the watched folder (parent of watched file)
     */
    public Path watchedFolder() {
        return fileReadoutHandler.getParentFolder();
    }

    /**
     * 'scheduler' thread's body. Runs in a loop until either stopped or interrupted,
     * with the following steps:
     *
     * <ol>
     *     <li>Poll the file system for file create, delete and update events
     *     This is performed with a timeout of 1 second.</li>
     *     <li>If there are any file events (created, deleted, updated), process them
     *     in thread 'readout', in the meantime 'scheduler' waits to complete 1 second.
     *     Actions per file event type:
     *         <ol>
     *             <li><b>created</b>: fully evict cache,
     *             rewind readout reader to start of file,
     *             readout and cache available lines.</li>
     *             <li><b>deleted</b>: fully evict cache,
     *             rewind readout reader to start of file.</li>
     *             <li><b>updated</b>: readout and cache available lines.
     *         </ol>
     *         The readout and cache inserting work is performed by the 'reader' thread,
     *         in parallel with the 'scheduler' thread. The 'readout' thread is only
     *         'waited' for by the 'scheduler' for the time difference between 1 second,
     *         and whatever time it took the poll system call to return (in the case there
     *         are events). This time is adjusted dynamically at runtime to allow a window
     *         for 'readout', 10L millis to start. In addition, if the file does not exist,
     *         there is a propagation delay from the time it is created to the time it is
     *         cached and ready for use by the Scheduler.
     *         These two delays add up to make the Scheduler soft real time, it will always
     *         be looking at the events within the file after the fact by some delta.
     *     </li>
     *     <li>Execute schedules: Schedules are processed by a single threaded executor
     *     ('schedules-processor' thread) to ensure they are triggered serially, asynchronously
     *     with the other threads, and in a relation happens-before with cache eviction, keeping
     *     in the cache only the minimum number of entries to satisfy the schedule of highest
     *     period. These are the steps taken:
     *          <ol>
     *              <li>Determine the schedules in schedule, ready to be executed.</li>
     *              <li>Determine the highest period across all schedules.</li>
     *              <li>Check whether the current tick is a multiple of it, in which
     *              case the cache can be fully evicted once the present schedules have
     *              been processed.</li>
     *              <li>Execute the schedules sequentially (the ready set), sorted by
     *              period, smallest to largest. The last schedule evicts the cache by
     *              the count of events it
     *              has seen.</li>
     *          </ol>
     *      </li>
     *      <li>Increment tick count. This only happens when the Scheduler has seen data.</li>
     *      <li>Check that the watched parent folder still exists. If not, the 'scheduler'
     *      thread will terminate, stopping the application.</li>
     *
     * </ol>
     */
    @Override
    public void run() {
        LOGGER.info("Starts");
        long ticks = 1L;
        dataAvailable.set(false);
        LOGGER.info("Read file from start: {}", readFileFromTheStart ? "Yes" : "No");
        if (!readFileFromTheStart && fileReadoutHandler.getFileReadOffset() == 0) {
            boolean fileExists = fileReadoutHandler.moveToEnd();
            if (!fileExists) {
                LOGGER.info("Alerts will start as soon as there is data available");
            }
        }
        long timeAdjustment = 10L;
        while (!Thread.currentThread().isInterrupted() && isRunning()) {
            try {
                long startTs = System.currentTimeMillis();
                WatchKey key = watchService.poll(1000L - timeAdjustment, TimeUnit.MILLISECONDS);
                if (key != null) {
                    processEvents(key);
                }
                timeAdjustment = maybeWaitToCompleteOneSecond(startTs, timeAdjustment);
                if (dataAvailable.get()) {
                    schedulesProcessor.processSchedules(ticks);
                    ticks++;
                }
                if (folderIsNotAccessible(watchedFolder())) {
                    LOGGER.info("Parent folder is not accessible, stopping");
                    break;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                LOGGER.info("Interrupted");
                // fall through
            } catch (ClosedWatchServiceException e) {
                LOGGER.error("WatchService is closed", e);
                break;
            }
        }
        LOGGER.info("Ended");
        stop();
    }

    private static long maybeWaitToCompleteOneSecond(long startTs, long adjustment) throws InterruptedException {
        long elapsed = System.currentTimeMillis() - startTs;
        long newAdjustment = adjustment;
        if (elapsed < 1000L) {
            long wait = 1000L - elapsed - 1L;
            newAdjustment -= 2L;
            TimeUnit.MILLISECONDS.sleep(wait);
        } else if (elapsed > 1000L) {
            newAdjustment += (elapsed - 1000L);
        }
        return newAdjustment;
    }

    private void processEvents(WatchKey key) {
        try {
            for (WatchEvent<?> event : key.pollEvents()) {
                if (fileReadoutHandler.fileMatches((Path) event.context())) {
                    String fileEventKind = event.kind().name();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("File event: {}", fileEventKind);
                    }
                    switch (WatchEventKind.kindOf(fileEventKind)) {
                        case ENTRY_CREATE -> processTask(() -> {
                            try {
                                readoutCache.fullyEvict();
                                fileReadoutHandler.moveToStart();
                                readoutCache.addAll(fileReadoutHandler.fetchAvailableLines());
                                dataAvailable.set(readoutCache.size() > 0);
                            } catch (IOException e) {
                                LOGGER.error("Cannot read: " + fileReadoutHandler.getFile(), e);
                            }
                        });
                        case ENTRY_DELETE -> {
                            readoutCache.fullyEvict();
                            fileReadoutHandler.moveToStart();
                            dataAvailable.set(false);
                        }
                        case ENTRY_MODIFY -> processTask(() -> {
                            try {
                                readoutCache.addAll(fileReadoutHandler.fetchAvailableLines());
                                dataAvailable.compareAndSet(false, readoutCache.size() > 0);
                            } catch (IOException e) {
                                LOGGER.error("Cannot read: " + fileReadoutHandler.getFile(), e);
                            }
                        });
                        case UNEXPECTED -> LOGGER.warn("Ignoring unexpected event kind: {}", fileEventKind);
                    }
                }
            }
        } finally {
            key.reset();
        }
    }

    /**
     * Starts the application.
     */
    @Override
    public synchronized void start() {
        if (isRunning()) {
            throw new IllegalStateException("already running");
        }
        if (folderIsNotAccessible(watchedFolder())) {
            throw new IllegalStateException("cannot access parent folder: " +
                    this.fileReadoutHandler.getParentFolder());
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
            watchedFolderKey = watchedFolder().register(
                    watchService, WATCH_EVENTS, SensitivityWatchEventModifier.HIGH);
        } catch (IOException e) {
            throw new IllegalStateException("could not start", e);
        }
        schedulesProcessor.start();
        super.start();
        schedulerThreadHandle = processTask(this);
        LOGGER.info("Started watching [{}]: {}",
                watchedFolder(), fileReadoutHandler.getFile().getFileName());
    }

    /**
     * Stops the application.
     */
    public synchronized void stop() {
        if (!isRunning()) {
            throw new IllegalStateException("not running");
        }
        LOGGER.info("Stopping");
        try {
            watchedFolderKey.cancel(); // receive no further events
            schedulerThreadHandle.cancel(true); // stop main thread
            try {
                watchService.close(); // dispose of watch service
            } catch (IOException ioe) {
                LOGGER.error("Trouble closing watchService", ioe);
            }
            super.stop();
            schedulesProcessor.stop();

        } finally {
            watchService = null;
            watchedFolderKey = null;
            schedulerThreadHandle = null;
            LOGGER.info("Stopped");
        }
    }

    private static boolean folderIsNotAccessible(Path folder) {
        return !PARENT_FOLDER_ATTRS.stream().allMatch(attr -> attr.apply(folder));
    }
}
