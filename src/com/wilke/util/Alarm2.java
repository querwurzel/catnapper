package com.wilke.util;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An alarm allows the execution of a specific action after a specific timeout.
 * The alarm can be reused / resetted at will.
 * After instantiation the alarm is not yet set, therefore call start() or reset().
 * An alarm instance is immutable and thread-safe.
 * After timeout the action is only triggered once!
 * 
 * (The successor of Alarm no longer using Timer and TimerTasks internally.)
 */
public final class Alarm2 {

	private final static ScheduledExecutorService scheduler =
			Executors.newScheduledThreadPool(1, new ThreadFactory() {
				private final AtomicLong counter = new AtomicLong();

				@Override
				public Thread newThread(final Runnable runnable) {
					final Thread thread = new Thread(runnable);
					thread.setName(Alarm2.class.getName() + "-Task#" + this.counter.incrementAndGet());
					thread.setDaemon(Boolean.TRUE);
					return thread;
				}
			});

	private final Runnable action;
	private final TimeUnit timeUnit;
	private final long timeout;

	private ScheduledFuture<?> alarm;

	/**
	 * @param timeout delay in milliseconds
	 * @exception NullPointerException if task is null
	 * @exception IllegalArgumentException if timeout < 1
	 */
	public Alarm2(final long timeout, final Runnable action) {
		this(timeout, TimeUnit.MILLISECONDS, action);
	}

	/**
	 * @exception NullPointerException if task or time unit is null
	 * @exception IllegalArgumentException if timeout < 1
	 */
	public Alarm2(final long timeout, final TimeUnit timeUnit, final Runnable action) {
		if (timeout < 1)
			throw new IllegalArgumentException();

		this.action   = Objects.requireNonNull(action);
		this.timeUnit = Objects.requireNonNull(timeUnit);
		this.timeout  = timeout;
	}

	public long getTimeout() {
		return this.timeout;
	}

	public TimeUnit getTimeUnit() {
		return this.timeUnit;
	}

	@Deprecated
	public void start() {
		this.reset();
	}

	public synchronized void stop() {
		if (this.alarm == null)
			return;

		this.alarm.cancel(Boolean.FALSE); // let them run once triggered
		this.alarm = null;
	}

	public synchronized void reset() {
		this.stop();
		this.alarm = scheduler.schedule(() -> {
            try {
                Alarm2.this.action.run();
            } finally {
                Alarm2.this.stop(); // upkeep
            }
        }, this.timeout, this.timeUnit);
	}
}
