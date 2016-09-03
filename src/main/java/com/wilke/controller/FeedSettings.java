package com.wilke.controller;

import static com.wilke.controller.FeedFilter.FEED_AGGREGATE;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.wilke.CatnapperContext;
import com.wilke.feed.FeedAggregate;

@WebServlet(
		name="FeedSettings",
		urlPatterns={"/Settings/*", "/settings/*"})
public class FeedSettings extends HttpServlet {

	private static final long serialVersionUID = 3526351682534495779L;

	private static final String NEW_FEED_URLS = "newFeedUrls";

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		final FeedAggregate aggregate = (FeedAggregate)request.getAttribute(FEED_AGGREGATE);
		final HttpSession session = request.getSession(Boolean.FALSE);
		String feedUrls;

		if (session != null && session.getAttribute(NEW_FEED_URLS) != null) {
			feedUrls = (String)session.getAttribute(NEW_FEED_URLS);
			session.removeAttribute(NEW_FEED_URLS); // Grails-like flash storage, sigh.
		} else {
			feedUrls = aggregate.fileContent;
		}

		response.setHeader("Cache-Control","public, max-age=12"); // at least 10 seconds in accordance with the JsonStore
		response.setDateHeader("Expires", System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(12));
		response.setContentType("text/html;charset=UTF-8");

		request.setAttribute("title", aggregate.title);
		request.setAttribute("feedUrls", feedUrls);
		request.getRequestDispatcher("/WEB-INF/index.jsp").forward(request, response);
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		final FeedAggregate aggregate = (FeedAggregate)request.getAttribute(FEED_AGGREGATE);

		request.getSession().setAttribute(NEW_FEED_URLS, CatnapperContext.instance().setAggregate(aggregate, request.getParameter("feedUrls")));

		response.sendRedirect( request.getRequestURL().toString() );
	}
}
