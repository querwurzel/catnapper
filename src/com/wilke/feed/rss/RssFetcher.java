package com.wilke.feed.rss;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletResponse;

import com.wilke.CatnapperConf;

@WebListener
public class RssFetcher implements ServletContextListener {

	private static final RssFeed END_OF_QUEUE = new RssFeed(); // poison pill

	private static final ExecutorService ioPool = Executors.newFixedThreadPool(CatnapperConf.maxConcTasks());

	private static final ExecutorService collectorPool = Executors.newFixedThreadPool(CatnapperConf.maxConcTasks());

	/**
	 * No automatic feed type recognition, RSS 2.0 is expected!
	 * HTTP/S is expected!
	 * 
	 * The iterator returned is not thread-safe. Its hasNext() method may block for 15 seconds at most.
	 */
	public static Iterator<RssFeed> fetchFeeds(final List<String> urls) {
		final BlockingQueue<RssFeed> queue = new ArrayBlockingQueue<>(urls.size() + 1); // including poison pill

		collectorPool.submit(new RssFetchCollector(queue, urls));

		return new Iterator<RssFeed>() {
			private boolean isClosed;
			private RssFeed nextItem;

			@Override
			public boolean hasNext() {
				if (this.isClosed)
					return Boolean.FALSE;

				try {
					final RssFeed feed = queue.poll(15, TimeUnit.SECONDS);

					if (feed == null || feed == END_OF_QUEUE)
						return !(this.isClosed = Boolean.TRUE);

					return (this.nextItem = feed) != null;
				} catch (final InterruptedException e) {
					throw new ConcurrentModificationException(e);
				}
			}

			@Override
			public RssFeed next() {
				if (this.nextItem != null || this.hasNext()) {
					final RssFeed nextItem = this.nextItem;
					this.nextItem    = null;
					return nextItem;
				}

				throw new NoSuchElementException();
			}

			@Override
			public void remove() {
				this.nextItem = null;
			}
		};
	}

	private static class RssFetchCollector implements Runnable {
		private final CompletionService<RssFeed> collector = new ExecutorCompletionService<RssFeed>(ioPool);
		private final BlockingQueue<RssFeed> queue;
		private final List<String> urls;

		public RssFetchCollector(final BlockingQueue<RssFeed> queue, final List<String> urls) {
			this.queue = queue;
			this.urls  = urls;
		}

		@Override
		public void run() {
			for (final String url : this.urls)
				this.collector.submit(new RssFetchTask(url));

			for (final String url : this.urls)
				try {
					this.queue.add(this.collector.take().get()); // each call blocks
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}

			this.queue.add(RssFetcher.END_OF_QUEUE);
		}
	}

	private static class RssFetchTask implements Callable<RssFeed> {
		private final String url;

		public RssFetchTask(final String url) {
			this.url = url;
		}

		@Override
		public RssFeed call() throws Exception {
			final URL safeUrl = new URL(this.url);

			if (!"http".equals(safeUrl.getProtocol()) && !"https".equals(safeUrl.getProtocol()))
				throw new MalformedURLException("HTTP/S is expected: " + this.url);

			// dependent of system proxy
			final HttpURLConnection conn = (HttpURLConnection)safeUrl.openConnection();
			conn.setConnectTimeout(3000);
			conn.setReadTimeout(7000);
			conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64; Trident/7.0; rv:11.0) like Gecko");
			conn.setRequestProperty("Accept", "application/rss+xml, application/xhtml+xml, text/xml");
			conn.setRequestProperty("Accept-Charset", "UTF-8");
			conn.setUseCaches(Boolean.FALSE);
			// possible, but not yet implemented
			//con.setRequestProperty("Accept-Encoding", "gzip");
			conn.connect();

			if (conn.getResponseCode() != HttpServletResponse.SC_OK) // 200
				throw new IOException(
						String.format("Feed not fetched (HTTP %d): %s", conn.getResponseCode(), this.url));

			return RssParser.parseFeed(conn.getInputStream());
		}
	}

	@Override
	public void contextInitialized(final ServletContextEvent event) {}

	@Override
	public void contextDestroyed(final ServletContextEvent event) {
		RssFetcher.ioPool.shutdown();
		RssFetcher.collectorPool.shutdown();
	}
}
