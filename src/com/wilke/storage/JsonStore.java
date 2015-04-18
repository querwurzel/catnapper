package com.wilke.storage;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParsingException;

import com.wilke.feed.FeedAggregate;
import com.wilke.util.Alarm2;

public final class JsonStore implements Closeable {

	// maps feed identifier to feed aggregates
	private final Map<String, FeedAggregate> store = new HashMap<>();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private final Path path;

	private final Alarm2 alarm = new Alarm2(TimeUnit.SECONDS.toMillis(10), new Runnable() {
		@Override
		public void run() {
			JsonStore.this.scheduleFileScanNow();
		}});

	private final Thread fileWatcher = new Thread() {
		@Override
		public void run() {
			try (WatchService service = FileSystems.getDefault().newWatchService()) {
				JsonStore.this.path.register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
				WatchKey key = null;
				do {
					try {
						key = service.take();
						if (!key.pollEvents().isEmpty())
							JsonStore.this.scheduleFileScan();
					} catch (final InterruptedException e) {
						return; // expected on close() call
					}
				} while (key.reset());
			} catch (final IOException e) {
				e.printStackTrace();
			}
		}
	};

	private static class JsonFormat {
		public static final String IDENTIFIER = "identifier";
		public static final String TITLE = "title";
		public static final String DESCRIPTION = "description";
		public static final String URLS = "urls";
	}

	public JsonStore(final String path) throws InvalidPathException {
		this.path = Paths.get(path);

		this.scheduleFileScanNow();

		this.fileWatcher.setDaemon(Boolean.TRUE);
		this.fileWatcher.start();
	}

	/**
	 * Stops file watching (cannot be turned on again). Refreshing the JsonStore must then be triggered manually.
	 */
	@Override
	public void close() {
		this.fileWatcher.interrupt();
		this.alarm.stop();
	}

	public FeedAggregate getAggregate(final String identifier) {
		try {
			this.lock.readLock().lock();
			return this.store.get(identifier);
		} finally {
			this.lock.readLock().unlock();
		}
	}

	public void scheduleFileScan() {
		this.alarm.reset();
	}

	public void scheduleFileScanNow() {
		final List<Path> files = this.collectFiles();
		final List<FeedAggregate> feeds = new ArrayList<>();

		for (final Path file : files)
			try {
				feeds.add( this.parseFile(file) );
			} catch (final JsonException e) {
				e.printStackTrace(); // log and continue
			}

		try {
			this.lock.writeLock().lock();
			this.store.clear();
			for (final FeedAggregate feed : feeds)
				this.store.put(feed.identifier, feed);
		} finally {
			this.lock.writeLock().unlock();
		}
	}

	private List<Path> collectFiles() {
		final List<Path> files = new ArrayList<>();
		final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**.json");

		try {
			Files.walkFileTree(this.path, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
					if (matcher.matches(file))
						files.add(file);

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (final IOException e) {
			e.printStackTrace();
		}

		return files;
	}

	private FeedAggregate parseFile(final Path path) throws JsonException {
		try (InputStream in = new FileInputStream(path.toFile())) {
			final JsonObject json = Json.createReader(in).readObject();
			if (json.isEmpty())
				throw new JsonException("JSON file is empty");

			final FeedAggregate feed = new FeedAggregate();
			// Deprecated
			feed.fileName = path.toAbsolutePath().toString();
			feed.fileContent = json.getJsonArray(JsonFormat.URLS).toString();
			feed.identifier = json.getString(JsonFormat.IDENTIFIER, null);
			if (feed.identifier == null || feed.identifier.isEmpty())
				throw new JsonException("JSON feed item does not have an identifier (" + json + ")");

			feed.title = json.getString(JsonFormat.TITLE);
			feed.description = json.getString(JsonFormat.DESCRIPTION);

			final JsonArray urls = json.getJsonArray(JsonFormat.URLS);
			for (int idx = 0; idx < urls.size(); idx++)
				feed.urls.add(urls.getString(idx));

			return feed;
		} catch (NullPointerException | ClassCastException | JsonParsingException | IOException e) {
			throw new JsonException(e.getMessage(), e);
		}
	}

	@Deprecated
	public String writeFile(final FeedAggregate aggregate, final String newUrls) {
		JsonArray urls;

		// user input validation, quick and dirty
		if (newUrls == null || newUrls.isEmpty())
			return newUrls;
		try (Reader reader = new StringReader(newUrls)) {
			urls = Json.createReader(reader).readArray();
			for (int idx = 0; idx < urls.size(); idx++)
				new URL(urls.getString(idx));
		} catch (JsonException | IOException e) {
			System.err.println("JsonStore Validation Error: " + e.getMessage());
			return newUrls; // user input syntax errors, abort
		}

		try (OutputStream output = new FileOutputStream(aggregate.fileName, Boolean.FALSE)) {
			final JsonObjectBuilder feed = Json.createObjectBuilder();
			feed.add(JsonFormat.IDENTIFIER, aggregate.identifier);
			feed.add(JsonFormat.TITLE, aggregate.title);
			feed.add(JsonFormat.DESCRIPTION, aggregate.description);
			feed.add(JsonFormat.URLS, urls);

			final Map<String, Boolean> config = new HashMap<>();
			config.put(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);

			Json.createWriterFactory(config).createWriter(output).writeObject(feed.build());
		} catch (final IOException e) {
			e.printStackTrace();
		}

		return newUrls;
	}
}
