package com.wilke.util;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

/**
 * An alarm allows the execution of a specific action after a specific timeout.
 * The alarm can be reused / resetted at will.
 * After instantiation the alarm is not yet set, therefore call start() or reset().
 * An alarm instance is immutable and thread-safe.
 * After timeout the action is only triggered once!
 * 
 * @deprecated replaced by {@link com.wilke.util.Alarm2}
 */
@Deprecated
public final class Alarm {
	private final static Timer timer = new Timer(Alarm.class.getName() + "-Timer", Boolean.TRUE);

	private final Runnable action;
	private final long timeout;

	// create initial reference with Null to prevent NullPointerException in stop()
	private WeakReference<TimerTask> alarm = new WeakReference<>(null);

	/**
	 * @param timeout delay in milliseconds
	 * @exception NullPointerException if task is null
	 * @exception IllegalArgumentException if timeout < 1
	 */
	public Alarm(final long timeout, final Runnable action) {
		if (timeout < 1)
			throw new IllegalArgumentException();

		this.action  = Objects.requireNonNull(action);
		this.timeout = timeout;
	}

	public long getTimeout() {
		return this.timeout;
	}

	@Deprecated
	public void start() {
		this.reset();
	}

	public synchronized void stop() {
		final TimerTask task = this.alarm.get(); // create strong reference, prevents GC
		if (task != null)
			task.cancel();
	}

	public synchronized void reset() {
		final TimerTask task = new TimerTask() {
			@Override
			public void run() {
				Alarm.this.action.run();
			}
		};

		this.stop();
		this.alarm = new WeakReference<>(task);
		timer.schedule(task, this.timeout);
	}
}
