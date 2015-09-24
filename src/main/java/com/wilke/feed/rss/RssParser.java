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
		// to catch &specialEntities;
		inputFactory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
	}

	/**
	 * Parses (only) the first channel and all of its items.
	 * @throws XMLStreamException
	 */
	public static RssFeed parseFeed(final InputStream stream) throws XMLStreamException {
		final XMLStreamReader in = inputFactory.createXMLStreamReader(stream);

		final RssFeed feed = new RssFeed();
		int numChannels = 0;

		while (in.hasNext()) {
			in.next();

			if (in.isStartElement()) {
				final String tag = in.getLocalName();

				switch (tag) {
				case RssFeed.RSS:
					continue;
				case RssChannel.CHANNEL:
					numChannels++;
					if (numChannels > 1)
			            throw new XMLStreamException("Feed has more than one channel."); // TODO support multiple channels
					feed.channel = parseChannel(in);
					continue;
				default:
					log.info("Unsupported tag in rss: {}", tag);
					continue;
				}
			}
		}

		return feed;
	}

	private static RssChannel parseChannel(final XMLStreamReader in) throws XMLStreamException {
		final RssChannel channel = new RssChannel();

		while (in.hasNext()) {
			in.next();

			if (in.isStartElement() || in.isEndElement()) {
				final String tag = in.getLocalName();

				if (in.isStartElement()) {
					switch (tag) {
					case RssChannel.TITLE:
						channel.title = parseText(in);
						continue;
					case RssChannel.LINK:
						channel.link = parseText(in);
						continue;
					case RssChannel.DESCRIPTION:
						channel.description = parseText(in);
						continue;
					case RssItem.ITEM:
						channel.items.add(parseItem(in));
						continue;
					default:
						log.info("Unsupported tag in channel: {}", tag);
						continue;
					}
				} else { // isEndElement()
					if (tag.equals(RssChannel.CHANNEL))
						return channel;
				}
			}
		}

		throw new XMLStreamException("Channel was never closed");
	}

	private static RssItem parseItem(final XMLStreamReader in) throws XMLStreamException {
		final RssItem item = new RssItem();

		while (in.hasNext()) {
			in.next();

			if (in.isStartElement() || in.isEndElement()) {
				final String tag = in.getLocalName();

				if (in.isStartElement()) {
					switch (tag) {
					case RssItem.TITLE:
						item.title = parseText(in);
						continue;
					case RssItem.CATEGORY:
						item.category = parseText(in);
						continue;
					case RssItem.LINK:
						item.link = parseText(in);
						continue;
					case RssItem.GUID:
						item.guid = parseText(in);
						continue;
					case RssItem.DESCRIPTION:
						item.description = parseText(in);
						continue;
					case RssItem.PUBDATE:
						item.pubDate = parseText(in);
						continue;
					default:
						log.info("Unsupported tag in item: {}", tag);
						continue;
					}
				} else { // isEndElement()
					if (tag.equals(RssItem.ITEM))
						return item;
				}
			}
		}

		throw new XMLStreamException("Item was never closed");
	}

	private static String parseText(final XMLStreamReader in) throws XMLStreamException {
		in.next();
		return in.isCharacters() ? in.getText() : "";
	}
}
