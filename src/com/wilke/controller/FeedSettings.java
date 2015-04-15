package com.wilke.controller;

import static com.wilke.controller.FeedFilter.feedIdentifier;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.wilke.CatnapperConf;
import com.wilke.feed.FeedAggregate;

@WebServlet(
		name="FeedSettings",
		urlPatterns={"/Settings/*", "/settings/*"})
public class FeedSettings extends HttpServlet {

	private static final long serialVersionUID = 3526351682534495779L;

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		final FeedAggregate aggregate = (FeedAggregate)request.getAttribute(feedIdentifier);

		request.setAttribute("title", aggregate.title);
		request.setAttribute("feed", aggregate.fileContent);
		request.getRequestDispatcher("/WEB-INF/index.jsp").forward(request, response);
	}

	@Override
	protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		final FeedAggregate aggregate = (FeedAggregate)request.getAttribute(feedIdentifier);

		CatnapperConf.setAggregate(aggregate, request.getParameter("feed"));

		response.sendRedirect( request.getRequestURL().toString() );
	}
}
