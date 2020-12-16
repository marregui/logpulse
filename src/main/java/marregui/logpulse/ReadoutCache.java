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

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Holds a list of ordered UTC Epoch timestamped entries allowing to
 * fetch them by interval (both ends included), as well as to evict
 * them (oldest entries first).
 * <p>
 * The cache method {@linkplain #addAll(List)} expects the list of
 * entries to be already sorted, as internal searches are done
 * using a binary partition method on the timestamp space.
 *
 * @param <T> a class implementing {@link WithUTCTimestamp}
 */
public class ReadoutCache<T extends WithUTCTimestamp> {

    /**
     * No entry was found (-1L).
     */
    public static final long NO_VALUE = -1L;

    private static final long INIT_VALUE = Long.MAX_VALUE;
    private static final Logger LOGGER = LoggerFactory.getLogger(ReadoutCache.class);

    private final Lock readLock;
    private final Lock writeLock;
    private List<T> entries;
    private long startTs;

    /**
     * Constructor.
     */
    public ReadoutCache() {
        entries = new ArrayList<>();
        ReadWriteLock entriesLock = new ReentrantReadWriteLock();
        readLock = entriesLock.readLock();
        writeLock = entriesLock.writeLock();
        startTs = INIT_VALUE;
    }

    /**
     * @return first UTC Epoch seen across the contents of the cache, or NO_VALUE (-1L)
     */
    public long firstTimestamp() {
        readLock.lock();
        try {
            return startTs == INIT_VALUE ? NO_VALUE : startTs;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * @param lastTimestamp UTC Epoch representing the last timestamp seen
     * @return first UTC Epoch after lastTimestamp, or NO_VALUE (-1L)
     */
    public long firstTimestampSince(long lastTimestamp) {
        readLock.lock();
        try {
            if (startTs == INIT_VALUE) {
                return NO_VALUE;
            }
            int idx = slideForward(entries, findNearest(entries, lastTimestamp)) + 1;
            return idx >= entries.size() ? NO_VALUE : timestampAt(entries, idx);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * @return true if the cache is empty
     */
    public boolean isEmpty() {
        readLock.lock();
        try {
            return entries.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * @return size of the cache
     */
    public int size() {
        readLock.lock();
        try {
            return entries.size();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Adds the entry to the cache.
     * The cache's order is not guaranteed, as the entry
     * is simply appended. Use several calls to this method
     *
     * @param entry to be added to the cache
     */
    public void add(T entry) {
        if (entry != null) {
            writeLock.lock();
            try {
                entries.add(entry);
                entries.sort(WithUTCTimestamp.COMPARING);
                startTs = Math.min(startTs, entry.getUTCTimestamp());
            } finally {
                writeLock.unlock();
            }
        }
    }

    /**
     * Adds the entries to the cache.
     *
     * @param newEntries to be added to the cache
     */
    public void addAll(List<T> newEntries) {
        if (newEntries != null && !newEntries.isEmpty()) {
            newEntries.sort(WithUTCTimestamp.COMPARING);
            writeLock.lock();
            try {
                entries.addAll(newEntries);
                startTs = Math.min(startTs, timestampAt(newEntries, 0));
            } finally {
                writeLock.unlock();
                LOGGER.debug("Added {} entries", newEntries.size());
            }
        }
    }

    /**
     * @param periodStart a UTC Epoch representing the period's start time
     * @param periodEnd   a UTC Epoch representing the period's end time (inclusive)
     * @return cache line containing the available entries for the period,
     * both ends inclusive
     */
    public List<T> fetch(long periodStart, long periodEnd) {
        List<T> cacheLine;
        int startIdx;
        int endIdx;
        readLock.lock();
        try {
            if (entries.isEmpty()) {
                return Collections.emptyList();
            }
            startIdx = slideBack(entries, findNearest(entries, periodStart));
            endIdx = slideForward(entries, findNearest(entries, periodEnd));
            cacheLine = startIdx == endIdx ?
                    Collections.singletonList(entries.get(startIdx))
                    :
                    new ArrayList<>(entries.subList(startIdx, endIdx + 1));
        } finally {
            readLock.unlock();
        }
        LOGGER.debug("Fetch count: {}, from: {} to: {}, startIdx: {}, endIdx (inclusive): {}",
                cacheLine.size(),
                UTCTimestamp.formatForDisplay(periodStart),
                UTCTimestamp.formatForDisplay(periodEnd),
                startIdx,
                endIdx);
        return cacheLine;

    }

    /**
     * Clears the contents of the cache.
     */
    public void fullyEvict() {
        int size;
        writeLock.lock();
        try {
            size = entries.size();
            entries.clear();
            startTs = INIT_VALUE;
        } finally {
            writeLock.unlock();
        }
        if (size > 0) {
            LOGGER.debug("Fully evicted");
        } else {
            LOGGER.debug("Cache is ready");
        }
    }

    /**
     * Removes count entries from the start of the cache.
     *
     * @param count number of entries to evict from the start
     */
    public void evict(int count) {
        int size = 0;
        writeLock.lock();
        try {
            size = entries.size();
            if (count > 0 && count < size) {
                entries = new ArrayList<>(entries.subList(count, size));
                startTs = timestampAt(entries, 0);
            } else {
                entries.clear();
                startTs = INIT_VALUE;
            }
            size = entries.size();
        } finally {
            writeLock.unlock();
            if (size > 0) {
                LOGGER.debug("Evicted count: {}, prev. size: {}, current size: {}",
                        count,
                        size + count,
                        size);
            } else {
                LOGGER.debug("Fully evicted");
            }
        }
    }

    /**
     * Traverses the entries list from idx, backwards fashion, while
     * the timestamp is the same as that found at index idx (truncating
     * from millisecond precision to just second, as the application's
     * resolution is 1 second).
     *
     * @param entries list of entries to traverse
     * @param idx     start offset
     * @param <T>     a class implementing {@link WithUTCTimestamp}
     * @return the first position within entries of the timestamp found
     * at position idx, travelling in the back in time direction
     */
    static <T extends WithUTCTimestamp> int slideBack(List<T> entries, int idx) {
        long ts = UTCTimestamp.truncateMillis(timestampAt(entries, idx));
        int i = idx;
        while (i >= 0 && UTCTimestamp.truncateMillis(timestampAt(entries, i)) == ts) {
            i--;
        }
        return i + 1;
    }

    /**
     * Traverses the entries list from idx, forwards fashion, while
     * the timestamp is the same as that found at index idx (truncating
     * from millisecond precision to just second, as the application's
     * resolution is 1 second).
     *
     * @param entries list of entries to traverse
     * @param idx     start offset
     * @param <T>     a class implementing {@link WithUTCTimestamp}
     * @return the last position within entries of the timestamp found
     * at position idx, travelling into the future
     */
    static <T extends WithUTCTimestamp> int slideForward(List<T> entries, int idx) {
        long ts = UTCTimestamp.truncateMillis(timestampAt(entries, idx));
        int i = idx;
        while (i < entries.size() && UTCTimestamp.truncateMillis(timestampAt(entries, i)) == ts) {
            i++;
        }
        return i - 1;
    }

    /**
     * Binary partition search where the result is one of the potentially
     * many positions of the timestamp, when timestamp is within the entries
     * list. Otherwise, the nearest in time within the list.
     *
     * @param entries   sorted list of entries
     * @param timestamp a UTC Epoch representing the target to find
     * @param <T>       a class implementing {@link WithUTCTimestamp}
     * @return the offset within the list, always some offset
     */
    static <T extends WithUTCTimestamp> int findNearest(List<T> entries, long timestamp) {
        int low = 0;
        int high = entries.size() - 1;
        if (timestamp < timestampAt(entries, low)) {
            return low;
        }
        if (timestamp > timestampAt(entries, high)) {
            return high;
        }
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int cmp = Long.compare(timestamp, timestampAt(entries, mid));
            if (cmp < 0) {
                high = mid - 1;
            } else if (cmp > 0) {
                low = mid + 1;
            } else {
                return mid;
            }
        }
        if (Math.abs(timestamp - timestampAt(entries, low)) >= Math.abs(timestamp - timestampAt(entries, high))) {
            return high;
        } else {
            return low;
        }
    }

    private static <T extends WithUTCTimestamp> long timestampAt(List<T> entries, int idx) {
        if (idx >= 0 && idx < entries.size()) {
            return entries.get(idx).getUTCTimestamp();
        }
        return NO_VALUE;
    }
}
