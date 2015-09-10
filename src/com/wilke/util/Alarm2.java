package com.wilke.util;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * An alarm allows the execution of a specific action after a specific timeout.
 * The alarm can be reused / reset at will.
 * After instantiation the alarm is not yet set, therefore call reset() or start().
 * An alarm instance is immutable and thread-safe.
 * After timeout the action is only triggered once!
 *
 * (The successor of Alarm no longer using Timer and TimerTasks internally.)
 */
public final class Alarm2 {

	private final static ScheduledExecutorService scheduler =
			Executors.newScheduledThreadPool(1, DaemonThreadFactory.INSTANCE);

	private final Runnable action;
	private final TimeUnit timeUnit;
	private final long timeout;

	private ScheduledFuture<?> alarm;

	/**
	 * @param timeout delay in seconds
	 * @exception NullPointerException if task is null
	 * @exception IllegalArgumentException if timeout < 1
	 */
	public Alarm2(final Runnable action, final long timeout) {
		this(action, timeout, TimeUnit.SECONDS);
	}

	/**
	 * @exception NullPointerException if task or time unit is null
	 * @exception IllegalArgumentException if timeout < 1
	 */
	public Alarm2(final Runnable action, final long timeout, final TimeUnit timeUnit) {
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
