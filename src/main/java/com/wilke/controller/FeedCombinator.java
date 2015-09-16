package com.wilke.controller;

import static com.wilke.controller.FeedFilter.FEED_AGGREGATE;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.wilke.CatnapperConf;
import com.wilke.feed.FeedAggregate;
import com.wilke.feed.rss.RssCombinator;

@WebServlet(
		name="FeedCombinator",
		urlPatterns={"/Feed/*", "/feed/*"})
public final class FeedCombinator extends HttpServlet {

	private static final long serialVersionUID = -1262361487011189016L;

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		final FeedAggregate aggregate = (FeedAggregate)request.getAttribute(FEED_AGGREGATE);

		response.setHeader("Connection", "close");
		response.setContentType("application/rss+xml;charset=UTF-8");

		RssCombinator.aggregateFeed(response.getOutputStream(), aggregate);
	}

	/**
	 * Exploits the "If-Modified-Since" header of the conditional GET request
	 * to apply some caching behavior. Does not factor in possible updates
	 * of the user feed that might have occurred in the meantime.
	 */
	@Override
	protected long getLastModified(final HttpServletRequest request) {
		final long now = System.currentTimeMillis() / 1000L * 1000L; // seconds
		long ifModifiedSince;

		try {
			ifModifiedSince = request.getDateHeader("If-Modified-Since");
		} catch (final IllegalArgumentException e) {
			ifModifiedSince = -1L;
		}

		if (now > ifModifiedSince + TimeUnit.HOURS.toMillis(CatnapperConf.clientCacheTimeout()))
			return now; // last request is too long ago

		return ifModifiedSince; // no change since last request
	}
}
