package com.alkadev.utils.common;

import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class AsyncPools {
    private static final Set<MonitoredPool> pools = ConcurrentHashMap.newKeySet();
    private static ScheduledExecutorService watchdog;

    private AsyncPools() {
    }

    // bounded queue; when full the calling thread runs the task itself, so nothing is dropped and producers slow down.
    // a task running longer than slowAfterSeconds is reported once (it cannot be stopped from outside)
    public static ThreadPoolExecutor create(int threads, int maxQueued, int slowAfterSeconds, String threadPrefix, String owner, Logger logger) {
        AtomicLong lastWarning = new AtomicLong();
        AtomicLong callerRuns = new AtomicLong();
        RejectedExecutionHandler whenFull = (task, pool) -> {
            if (pool.isShutdown()) throw new RejectedExecutionException(owner + " is shut down");
            callerRuns.incrementAndGet();
            long now = System.currentTimeMillis();
            long last = lastWarning.get();
            if (now - last > 1000 && lastWarning.compareAndSet(last, now))
                logger.warn(owner + " async queue is full (" + maxQueued + "): callers run tasks themselves until it drains.");
            task.run();
        };
        MonitoredPool pool = new MonitoredPool(threads, maxQueued, threadPrefix, whenFull, owner, logger, slowAfterSeconds, callerRuns);
        pools.add(pool);
        startWatchdog();
        return pool;
    }

    // queue length, tasks running now, tasks finished, tasks the caller had to run itself because the queue was full, the longest running task in seconds
    public static Map<String, Object> stats(ExecutorService pool) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (pool instanceof MonitoredPool m) {
            out.put("queued", m.getQueue().size());
            out.put("active", m.getActiveCount());
            out.put("completed", m.getCompletedTaskCount());
            out.put("callerRuns", m.callerRuns.get());
            out.put("longestTaskSeconds", m.longestRunningSeconds());
        } else {
            out.put("queued", 0);
            out.put("active", 0);
            out.put("completed", 0L);
            out.put("callerRuns", 0L);
            out.put("longestTaskSeconds", 0L);
        }
        return out;
    }

    private static synchronized void startWatchdog() {
        if (watchdog != null) return;
        watchdog = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AlkaTaskWatchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleWithFixedDelay(() -> {
            try {
                for (MonitoredPool pool : pools) pool.reportSlowTasks();
            } catch (Exception ignored) {
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private static final class Running {
        final long startedAt = System.currentTimeMillis();
        boolean reported;
    }

    private static final class MonitoredPool extends ThreadPoolExecutor {
        private final Map<Thread, Running> running = new ConcurrentHashMap<>();
        private final String owner;
        private final Logger logger;
        private final int slowAfterSeconds;
        final AtomicLong callerRuns;

        MonitoredPool(int threads, int maxQueued, String threadPrefix, RejectedExecutionHandler handler, String owner, Logger logger, int slowAfterSeconds, AtomicLong callerRuns) {
            super(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(maxQueued), new ThreadFactoryWithNamePrefix(threadPrefix), handler);
            this.owner = owner;
            this.logger = logger;
            this.slowAfterSeconds = slowAfterSeconds;
            this.callerRuns = callerRuns;
        }

        long longestRunningSeconds() {
            long now = System.currentTimeMillis(), longest = 0;
            for (Running task : running.values()) longest = Math.max(longest, (now - task.startedAt) / 1000);
            return longest;
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            running.put(t, new Running());
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            running.remove(Thread.currentThread());
        }

        @Override
        protected void terminated() {
            pools.remove(this);
        }

        void reportSlowTasks() {
            long now = System.currentTimeMillis();
            for (Map.Entry<Thread, Running> entry : running.entrySet()) {
                Running task = entry.getValue();
                long seconds = (now - task.startedAt) / 1000;
                if (!task.reported && seconds >= slowAfterSeconds) {
                    task.reported = true;
                    logger.warn(owner + " async task on thread " + entry.getKey().getName() + " has been running for " + seconds
                            + "s (limit for this warning " + slowAfterSeconds + "s) - it may be stuck.");
                }
            }
        }
    }
}
