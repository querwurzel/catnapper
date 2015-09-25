package com.wilke.feed.rss;

import java.io.InputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wilke.feed.rss.RssFeed.RssChannel;
import com.wilke.feed.rss.RssFeed.RssItem;

public class RssParser {

	private static final Logger log = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

	private static final XMLInputFactory inputFactory = XMLInputFactory.newFactory();
	static {
		inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
		inputFactory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
		inputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
	}

	/**
	 * Parses (only) the first channel and all of its items.
	 * @throws XMLStreamException
	 */
	public static RssFeed parseFeed(final InputStream stream) throws XMLStreamException {
		final XMLStreamReader reader = inputFactory.createXMLStreamReader(stream);
		final RssFeed feed = new RssFeed();
		boolean isRSS = Boolean.FALSE;

		try {
			tagLoop:
			while (reader.hasNext()) {
				reader.next();

				if (reader.isStartElement()) {
					final String tag = reader.getLocalName();

					if (isRSS) { // RSS check passed
						switch (tag) {
						case RssChannel.CHANNEL:
							feed.channel = parseChannel(reader);
							break;
						default:
							log.debug("Unsupported tag in rss: {}", tag);
							continue;
						}
					} else { // RSS check
						if (RssFeed.RSS.equals(tag))
							for (int idx = 0; idx < reader.getAttributeCount(); idx++)
								if (RssFeed.VERSION_ATTRIBUTE.equals(reader.getAttributeLocalName(idx)))
									if (RssFeed.VERSION_VALUE.equals(reader.getAttributeValue(idx))) {
										isRSS = Boolean.TRUE;
										continue tagLoop;
									}

						throw new XMLStreamException("Invalid RSS feed, either none found or unsupported version");
					}
				}
			}
		} finally {
			if (reader != null)
				reader.close();
		}

		return feed;
	}

	private static RssChannel parseChannel(final XMLStreamReader reader) throws XMLStreamException {
		final RssChannel channel = new RssChannel();

		while (reader.hasNext()) {
			reader.next();

			if (reader.isStartElement() || reader.isEndElement()) {
				final String tag = reader.getLocalName();

				if (reader.isStartElement()) {
					switch (tag) {
					case RssChannel.TITLE:
						channel.title = parseText(reader);
						continue;
					case RssChannel.LINK:
						channel.link = parseText(reader);
						continue;
					case RssChannel.DESCRIPTION:
						channel.description = parseText(reader);
						continue;
					case RssItem.ITEM:
						channel.items.add(parseItem(reader));
						continue;
					default:
						log.debug("Unsupported tag in channel: {}", tag);
						continue;
					}
				} else { // isEndElement()
					if (tag.equals(RssChannel.CHANNEL))
						return channel;
				}
			}
		}

		throw new XMLStreamException("Invalid RSS channel, either none found or not well-formed");
	}

	private static RssItem parseItem(final XMLStreamReader reader) throws XMLStreamException {
		final RssItem item = new RssItem();

		while (reader.hasNext()) {
			reader.next();

			if (reader.isStartElement() || reader.isEndElement()) {
				final String tag = reader.getLocalName();

				if (reader.isStartElement()) {
					switch (tag) {
					case RssItem.TITLE:
						item.title = parseText(reader);
						continue;
					case RssItem.CATEGORY:
						item.category = parseText(reader);
						continue;
					case RssItem.LINK:
						item.link = parseText(reader);
						continue;
					case RssItem.GUID:
						item.guid = parseText(reader);
						continue;
					case RssItem.DESCRIPTION:
						item.description = parseText(reader);
						continue;
					case RssItem.PUBDATE:
						item.pubDate = parseText(reader);
						continue;
					default:
						log.debug("Unsupported tag in item: {}", tag);
						continue;
					}
				} else { // isEndElement()
					if (tag.equals(RssItem.ITEM))
						return item;
				}
			}
		}

		throw new XMLStreamException("Invalid RSS channel item, either none found or not well-formed");
	}

	private static String parseText(final XMLStreamReader reader) throws XMLStreamException {
		reader.next();
		return reader.isCharacters() ? reader.getText() : "";
	}
}
