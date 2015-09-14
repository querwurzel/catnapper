package com.wilke.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class DaemonThreadFactory implements ThreadFactory {

	public static final ThreadFactory INSTANCE = new DaemonThreadFactory();

	@Override
	public Thread newThread(Runnable runnable) {
		final Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setDaemon(Boolean.TRUE);
        return thread;
    };
}
