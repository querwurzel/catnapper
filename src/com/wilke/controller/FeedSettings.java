package com.wilke.controller;

import static com.wilke.controller.FeedFilter.feedIdentifier;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.wilke.CatnapperConf;
import com.wilke.feed.FeedAggregate;

@WebServlet(
		name="FeedSettings",
		urlPatterns={"/Settings/*", "/settings/*"})
public class FeedSettings extends HttpServlet {

	private static final long serialVersionUID = 3526351682534495779L;
	private static final String newFeed = "newFeed";

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		final FeedAggregate aggregate = (FeedAggregate)request.getAttribute(feedIdentifier);
		final HttpSession session = request.getSession(Boolean.FALSE);
		String feed;

		if (session != null && session.getAttribute(newFeed) != null) {
			feed = (String)session.getAttribute(newFeed);
			session.removeAttribute(newFeed); // Grails-like flash storage, sigh.
		} else {
			feed = aggregate.fileContent;
		}

		response.setHeader("Cache-Control","no-cache"); // HTTP 1.1
		response.setHeader("Pragma","no-cache"); // HTTP 1.0
		response.setDateHeader("Expires", 0); // prevents caching at the proxy server

		request.setAttribute("title", aggregate.title);
		request.setAttribute("feed", feed);
		request.getRequestDispatcher("/WEB-INF/index.jsp").forward(request, response);
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		final FeedAggregate aggregate = (FeedAggregate)request.getAttribute(feedIdentifier);

		request.getSession().setAttribute(newFeed, CatnapperConf.setAggregate(aggregate, request.getParameter("feed")));

		response.sendRedirect( request.getRequestURL().toString() );
	}
}
