package com.wilke.feed.rss;

import com.wilke.CatnapperContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RssFetcher {

	private static final Logger log = LoggerFactory.getLogger(RssFetcher.class);

	private static final ExecutorService executor = CatnapperContext.instance().asyncExecutorService();

	/**
	 * Timeout in milliseconds to be used when opening a communications link to a given URL.
	 */
	public static final int connectTimeout = 5000;

	/**
	 * Timeout in milliseconds to be used when a connection is established to a given URL to read all data.
	 */
	public static final int readTimeout = 5000;

	/**
	 * No automatic feed type recognition, RSS 2.0 via HTTP/S is expected!
	 *
	 * The iterator returned is not thread-safe.
	 * Its {@link java.util.Iterator#hasNext()} and {@link java.util.Iterator#next()} methods may block for {@value #connectTimeout}+{@value #readTimeout} milliseconds at most.
	 */
	public static Iterable<RssFeed> fetchFeeds(final List<String> urls) {
		final CompletionService<RssFeed> collector = new ExecutorCompletionService<>(executor);

		for (final String url : urls)
			collector.submit(new RssFetchTask(url));

		return () -> new RssFeedIterator(collector, urls.size());
	}

	private static class RssFetchTask implements Callable<RssFeed> {
        private final String originalUrl;
		private String currentUrl;

		public RssFetchTask(final String url) {
            this.originalUrl = url;
            this.currentUrl = this.originalUrl;
		}

		@Override
		public RssFeed call() throws IOException {
            for (int attempts = 3; attempts > 0; attempts--)
                try {
                    final URL url = new URL(this.currentUrl);
                    if (!"http".equals(url.getProtocol()) && !"https".equals(url.getProtocol()))
                        throw new MalformedURLException("HTTP/S expected");

                    // dependent of system proxy
                    final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(Boolean.TRUE);
                    conn.setUseCaches(Boolean.FALSE);
                    conn.setConnectTimeout(connectTimeout);
                    conn.setReadTimeout(readTimeout);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64; Trident/7.0; rv:11.0) like Gecko");
                    conn.setRequestProperty("Accept", "application/rss+xml, application/xhtml+xml, text/xml");
                    conn.setRequestProperty("Accept-Charset", "UTF-8");
                    conn.setRequestProperty("Accept-Encoding", "identity"); // TODO support gzip
                    conn.connect();

                    try (final InputStream stream = conn.getInputStream()) {
                        switch (conn.getResponseCode()) {
                            case HttpURLConnection.HTTP_MOVED_PERM:
                            case HttpURLConnection.HTTP_MOVED_TEMP:
                                final URL redirect = new URL(conn.getHeaderField("Location"));
                                this.currentUrl = redirect.toExternalForm();
                                continue;
                            case HttpURLConnection.HTTP_OK:
                                return RssParser.parseFeed(stream);
                            default:
                                throw new IOException(String.format("request unsuccessful (response code %d)", conn.getResponseCode()));
                        }
                    }
                } catch (IOException | XMLStreamException e) {
                    throw new IOException(this.originalUrl, e); // enrich exception by url
                }

            throw new IOException(this.originalUrl, new IOException("maximum number of redirects reached"));
        }
	}

	private static class RssFeedIterator implements Iterator<RssFeed> {
		private final CompletionService<RssFeed> collector;
		private final int numOfTasks;
		private RssFeed nextItem;
		private boolean isClosed;

		public RssFeedIterator(final CompletionService<RssFeed> collector, final int numOfTasks) {
			this.collector = collector;
			this.numOfTasks = numOfTasks;
		}

		@Override
        public boolean hasNext() {
            if (this.isClosed)
                return Boolean.FALSE;

            try {
                for (int count = 1; count <= numOfTasks; count++) {
                    final Future<RssFeed> future = collector.poll(connectTimeout + readTimeout + 500, TimeUnit.MILLISECONDS);
                    if (future == null) { // once the timeout has been reached and no (further) task returned, assume complete failure
                        log.warn("Timeout reached, {} RSS feeds outstanding", this.numOfTasks - count);
                        break;
                    }

                    try {
                        this.nextItem = future.get(); // calling get() since a future was returned supposedly holding a result already
                        if (this.nextItem == null)
                            continue;

                        return Boolean.TRUE;
                    } catch (final CancellationException | ExecutionException e) {
                        final StringBuilder trace = new StringBuilder("RSS could not be fetched");
                        for (Throwable t = e.getCause(); t != null; t = t.getCause())
                            trace.append(System.lineSeparator()).append("\t").append("Caused by: ").append(t.toString());
                        log.warn(trace.toString());
                    }
                }

                this.nextItem = null;
                return !(this.isClosed = Boolean.TRUE);
            } catch (final InterruptedException e) {
                this.nextItem = null;
                this.isClosed = Boolean.TRUE;
                throw new ConcurrentModificationException(e);
            }
        }

		@Override
        public RssFeed next() {
            if (this.nextItem == null && !this.hasNext())
                throw new NoSuchElementException();

            return this.nextItem;
        }

		@Override
        public void remove() {
			// throw new UnsupportedOperationException();
            if (this.isClosed || this.nextItem == null)
                throw new IllegalStateException();

            this.nextItem = null;
        }
	}
}
