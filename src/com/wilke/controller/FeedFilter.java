package com.wilke.controller;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.wilke.CatnapperConf;
import com.wilke.feed.FeedAggregate;

@WebFilter(servletNames={"FeedCombinator", "FeedSettings"})
public class FeedFilter implements Filter {

	public static final String feedIdentifier = "feedIdentifier";

	@Override
	public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
		final HttpServletRequest  httpReq  = (HttpServletRequest)request;
		final HttpServletResponse httpResp = (HttpServletResponse)response;

		final String feedIdentifier   = FeedFilter.getFeedIdentifier(httpReq.getRequestURI());
		final FeedAggregate aggregate = CatnapperConf.getAggregate(feedIdentifier);

		if (aggregate == null) { // abort further processing
			httpResp.reset();
			httpResp.setStatus(HttpServletResponse.SC_NOT_FOUND);
		} else {
			httpReq.setAttribute(FeedFilter.feedIdentifier, aggregate);
			chain.doFilter(httpReq, httpResp);
		}
	}

	public static String getFeedIdentifier(final String uri) {
		final int lastSlash = uri.lastIndexOf("/");

		// slash found and not the last character
		if (lastSlash != -1 && lastSlash < uri.length())
			return uri.substring(lastSlash + 1);

		return null;
	}

	@Override
	public void destroy() {}

	@Override
	public void init(final FilterConfig config) throws ServletException {}
}
