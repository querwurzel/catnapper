package com.wilke.controller;

import static com.wilke.controller.FeedFilter.feedIdentifier;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

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

		response.setHeader("Cache-Control","max-age=10"); // 10 seconds in accordance with the JsonStore
		response.setDateHeader("Expires", System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10));
		response.setContentType("text/html;charset=UTF-8");
		response.setHeader("Connection", "close");

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
