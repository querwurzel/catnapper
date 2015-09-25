package com.wilke.feed.rss;

import java.util.ArrayList;
import java.util.List;

public class RssFeed {

	public static final String RSS = "rss";
	public static final String VERSION_ATTRIBUTE = "version";
	public static final String VERSION_VALUE = "2.0";

	public RssChannel channel;

	public static class RssChannel {
		public static final String CHANNEL = "channel";
		public static final String TITLE = "title";
		public static final String LINK = "link";
		public static final String DESCRIPTION = "description";

		public String title;
		public String link;
		public String description;

		public final List<RssItem> items = new ArrayList<>();
	}

	public static class RssItem {
		public static final String ITEM = "item";
		public static final String TITLE = "title";
		public static final String CATEGORY = "category";
		public static final String LINK = "link";
		public static final String GUID = "guid";
		public static final String DESCRIPTION = "description";
		public static final String PUBDATE = "pubDate";

		public String title;
		public String category;
		public String link;
		public String guid;
		public String description;
		public String pubDate;
	}
}
