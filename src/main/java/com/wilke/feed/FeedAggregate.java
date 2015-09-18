package com.wilke.feed;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import com.wilke.feed.rss.RssFeed;
import com.wilke.feed.rss.RssFetcher;

//TODO RssFeed is way too specific, relax type!
public class FeedAggregate {

	/**
	 * @throws NullPointerException
	 */
	public FeedAggregate(final String identifier) {
		this.identifier = Objects.requireNonNull(identifier);
	}

	@Deprecated
	public transient String fileName;
	@Deprecated
	public transient String fileContent;

	public transient String link;

	public final String identifier;
	public String title;
	public String description;

	public final List<String> urls = new ArrayList<>();

	/**
	 * Immediately starts fetching the feeds represented by the URLs of this feed aggregate object in an asynchronous way.
	 * 
	 * @see com.wilke.feed.rss.RssFetcher#fetchFeeds(List)
	 */
	public Iterator<RssFeed> fetchFeeds() {
		return RssFetcher.fetchFeeds(this.urls);
	}
}
