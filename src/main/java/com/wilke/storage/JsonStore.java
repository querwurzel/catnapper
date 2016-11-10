package com.wilke.storage;

import com.wilke.feed.FeedAggregate;
import com.wilke.util.Alarm2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonParsingException;
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

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public final class JsonStore implements Closeable {

	private static final Logger log = LoggerFactory.getLogger(JsonStore.class);

	private final Path path;

	private final Alarm2 alarm = new Alarm2(() -> JsonStore.this.scheduleFileScanNow(), 10);

    // maps feed identifier to feed aggregate
    private volatile Map<String, FeedAggregate> store = new HashMap<>();

	private final Thread fileWatcher = new Thread() {
		@Override
		public void run() {
			try (WatchService service = FileSystems.getDefault().newWatchService()) {
				JsonStore.this.path.register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
				WatchKey key;
				do {
					try {
						key = service.take();
						if (!key.pollEvents().isEmpty())
							JsonStore.this.scheduleFileScan();
					} catch (final InterruptedException e) {
						log.debug("FileWatcher has been interrupted, shutting down.");
						return; // expected on close() call
					}
				} while (key.reset());
			} catch (final IOException e) {
				log.error(e.toString());
			}
		}
	};

	private final class JsonFormat {
		public static final String IDENTIFIER = "identifier";
		public static final String TITLE = "title";
		public static final String DESCRIPTION = "description";
		public static final String URLS = "urls";
	}

	public JsonStore(final String path) throws InvalidPathException {
		this.path = Paths.get(path);
		this.fileWatcher.setDaemon(Boolean.TRUE);
		this.fileWatcher.start();
		this.scheduleFileScan();
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
        return this.store.get(identifier);
	}

	public void scheduleFileScan() {
		this.alarm.reset();
	}

	public void scheduleFileScanNow() {
		final List<Path> files = this.collectFiles();
        final Map<String, FeedAggregate> feeds = new HashMap<>();

		for (final Path file : files)
			try {
                final FeedAggregate aggregate = this.parseFile(file);
                feeds.put(aggregate.identifier, aggregate);
			} catch (final JsonException e) {
				log.warn("Could not parse json file: {}", e.toString()); // log and continue
			}

        this.store = feeds;
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
			log.warn("Could not scan file tree: {}", e.toString());
		}

		return files;
	}

	private FeedAggregate parseFile(final Path path) throws JsonException {
		try (InputStream in = new FileInputStream(path.toFile())) {
			final JsonObject json = Json.createReader(in).readObject();
			if (json.isEmpty())
				throw new JsonException("JSON file is empty");

			final String identifier = json.getString(JsonFormat.IDENTIFIER, null);
			if (identifier == null || identifier.isEmpty())
				throw new JsonException("JSON feed item does not have an identifier [" + json + "]");

			final FeedAggregate feed = new FeedAggregate(identifier);
			feed.title = json.getString(JsonFormat.TITLE);
			feed.description = json.getString(JsonFormat.DESCRIPTION);
			feed.fileName = path.toAbsolutePath().toString();
			feed.fileContent = json.getJsonArray(JsonFormat.URLS).toString();

			final JsonArray urls = json.getJsonArray(JsonFormat.URLS);
			for (int idx = 0; idx < urls.size(); idx++)
				feed.urls.add(urls.getString(idx));

			return feed;
		} catch (NullPointerException | ClassCastException | JsonParsingException | IOException e) {
			throw new JsonException(e.getMessage(), e);
		}
	}

	private static final Map<String, Boolean> jsonConfig = new HashMap<>();
	static {
		jsonConfig.put(JsonGenerator.PRETTY_PRINTING, Boolean.TRUE);
	}

	@Deprecated
	public String writeFile(final FeedAggregate aggregate, String newUrls) {
		JsonArray urls;

		// user input validation, quick and dirty
		if (newUrls == null || newUrls.isEmpty())
			newUrls = "[]"; // empty JSON array
		try (Reader reader = new StringReader(newUrls)) {
			urls = Json.createReader(reader).readArray();
			for (int idx = 0; idx < urls.size(); idx++)
				new URL(urls.getString(idx));
		} catch (JsonException | IOException e) {
			log.debug("User input validation error: {}", e.toString());
			return newUrls; // user input syntax errors, abort
		}

		synchronized (aggregate.identifier) {
			try (OutputStream output = new FileOutputStream(aggregate.fileName, Boolean.FALSE)) {
				final JsonObjectBuilder feed = Json.createObjectBuilder();
				feed.add(JsonFormat.IDENTIFIER, aggregate.identifier);
				feed.add(JsonFormat.TITLE, aggregate.title);
				feed.add(JsonFormat.DESCRIPTION, aggregate.description);
				feed.add(JsonFormat.URLS, urls);

				Json.createWriterFactory(jsonConfig).createWriter(output).writeObject(feed.build());
			} catch (final IOException e) {
				log.warn("Could not save json file: {}", e.toString());
			}
		}

		return newUrls;
	}
}
