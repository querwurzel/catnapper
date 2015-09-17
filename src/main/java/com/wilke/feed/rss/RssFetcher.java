package com.wilke.feed.rss;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.XMLStreamException;

import com.wilke.CatnapperConf;
import com.wilke.util.DaemonThreadFactory;

public class RssFetcher {

	private static final ExecutorService connectorPool =
			Executors.newFixedThreadPool(CatnapperConf.maxConcTasks(), DaemonThreadFactory.INSTANCE);

	/**
	 * Timeout in milliseconds to be used when opening a communications link to a given URL.
	 */
	public static final int connectTimeout = 4000;

	/**
	 * Timeout in milliseconds to be used when a connection is established to a given URL to read all data.
	 */
	public static final int readTimeout = 6000;

	/**
	 * No automatic feed type recognition, RSS 2.0 via HTTP/S is expected!
	 *
	 * The iterator returned is not thread-safe.
	 * Its {@link java.util.Iterator#hasNext()} and {@link java.util.Iterator#next()} methods may block for {@value #connectTimeout}+{@value #readTimeout} milliseconds at most.
	 */
	public static Iterator<RssFeed> fetchFeeds(final List<String> urls) {
		final CompletionService<RssFeed> collector = new ExecutorCompletionService<RssFeed>(connectorPool);

		for (final String url : urls)
			collector.submit(new RssFetchTask(url));

		return new Iterator<RssFeed>() {
			private boolean isClosed;
			private int count = urls.size();
			private RssFeed nextItem;

			@Override
			public boolean hasNext() {
				if (this.isClosed)
					return Boolean.FALSE;

				try {
					for (this.count--; this.count > 0;) {
						final Future<RssFeed> future = collector.poll(connectTimeout + readTimeout + 500, TimeUnit.MILLISECONDS);
						if (future == null) // not a single task returned despite 500ms extra time; assuming complete failure
							break;

						try {
							final RssFeed feed = future.get(); // calling get() since a future was returned supposedly holding a feed
							if (feed == null)
								continue;

							return (this.nextItem = feed) != null;
						} catch (final CancellationException | ExecutionException e) {
							e.getCause().printStackTrace();
						}
					}

					return !(this.isClosed = Boolean.TRUE);
				} catch (final InterruptedException e) {
					this.isClosed = Boolean.TRUE;
					throw new ConcurrentModificationException(e);
				}
			}

			@Override
			public RssFeed next() {
				if (this.nextItem == null && !this.hasNext())
					throw new NoSuchElementException();

				final RssFeed current = this.nextItem;
				this.nextItem = null;
				return current;
			}

			@Override
			public void remove() {
				if (this.nextItem == null && this.isClosed)
					throw new IllegalStateException();

				this.nextItem = null;
			}
		};
	}

	private static class RssFetchTask implements Callable<RssFeed> {
		private final String url;

		public RssFetchTask(final String url) {
			this.url = url;
		}

		@Override
		public RssFeed call() throws IOException {
			try {
				final URL safeUrl = new URL(this.url);
				if (!"http".equals(safeUrl.getProtocol()) && !"https".equals(safeUrl.getProtocol()))
					throw new MalformedURLException("HTTP/S is expected");

				// dependent of system proxy
				final HttpURLConnection conn = (HttpURLConnection)safeUrl.openConnection();
				conn.setConnectTimeout(connectTimeout);
				conn.setReadTimeout(readTimeout);
				conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64; Trident/7.0; rv:11.0) like Gecko");
				conn.setRequestProperty("Accept", "application/rss+xml, application/xhtml+xml, text/xml");
				conn.setRequestProperty("Accept-Charset", "UTF-8");
				conn.setRequestProperty("Accept-Encoding", "identity"); // TODO support gzip
				conn.setUseCaches(Boolean.FALSE);
				conn.connect();

				if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) // 200 (through naive)
					throw new IOException(String.format("Request unsuccessful (HTTP %d)", conn.getResponseCode()));

				return RssParser.parseFeed(conn.getInputStream());
			} catch (IOException | XMLStreamException e) {
				throw new IOException(this.url, e); // enrich exception by url
			}
		}
	}
}
