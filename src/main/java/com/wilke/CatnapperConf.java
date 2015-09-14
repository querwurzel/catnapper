package com.wilke;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import com.wilke.feed.FeedAggregate;
import com.wilke.storage.JsonStore;

@WebListener
public final class CatnapperConf implements ServletContextListener {

	private static volatile String pathToFeeds;
	private static volatile int maxConcTasks = Runtime.getRuntime().availableProcessors() * 2;
	private static volatile int clientCacheTimeout = 3;

	private static JsonStore store;

	@Override
	public void contextInitialized(final ServletContextEvent sce) {
		final ServletContext ctx = sce.getServletContext();

		// maximum concurrent tasks
		String param = ctx.getInitParameter("maxConcTasks");
		if (param != null && !param.isEmpty())
			CatnapperConf.maxConcTasks = Integer.valueOf(param);

		// timeout of client-side conditional get
		param = ctx.getInitParameter("clientCacheTimeout");
		if (param != null && !param.isEmpty())
			CatnapperConf.clientCacheTimeout = Integer.valueOf(param);

		// path for JsonStore
		param = ctx.getInitParameter("pathToFeeds");
		if (param == null || param.isEmpty()) {
			param = ctx.getRealPath("/WEB-INF/conf/");
			if (param == null)
				throw new RuntimeException("CatnapperConf: Failed to translate config folder to filesystem path. Make sure .war file was extracted!");
		}
		CatnapperConf.pathToFeeds = param;
		CatnapperConf.store = new JsonStore(CatnapperConf.pathToFeeds);

		System.out.println("Parameter (pathToFeeds)        : " + pathToFeeds);
		System.out.println("Parameter (maxConcTasks)       : " + maxConcTasks);
		System.out.println("Parameter (clientCacheTimeout) : " + clientCacheTimeout);
	}

	@Override
	public void contextDestroyed(final ServletContextEvent sce) {
		final JsonStore ref = CatnapperConf.store;
		if (ref != null)
			ref.close();
	}

	public static String pathToFeeds() {
		return CatnapperConf.pathToFeeds;
	}

	public static int maxConcTasks() {
		return CatnapperConf.maxConcTasks;
	}

	public static long clientCacheTimeout() {
		return CatnapperConf.clientCacheTimeout;
	}

	@Deprecated
	public static FeedAggregate getAggregate(final String identifier) {
		return store.getAggregate(identifier);
	}

	@Deprecated
	public static String setAggregate(final FeedAggregate aggregate, final String newUrls) {
		return store.writeFile(aggregate, newUrls);
	}
}
