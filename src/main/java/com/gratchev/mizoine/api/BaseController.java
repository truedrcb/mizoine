package com.gratchev.mizoine.api;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.gratchev.mizoine.FlexmarkComponent;
import com.gratchev.mizoine.SignedInUser;
import com.gratchev.mizoine.repository.Repositories;
import com.gratchev.mizoine.repository.Repository;
import com.vladsch.flexmark.util.ast.Node;

public abstract class BaseController {
	@Autowired
	private Repositories repos;
	@Autowired
	protected FlexmarkComponent flexmark;
	@Autowired
	protected SignedInUser currentUser;

	public Repository getRepo() {
		// https://stackoverflow.com/questions/3320674/spring-how-do-i-inject-an-httpservletrequest-into-a-request-scoped-bean
		final HttpServletRequest currentRequest = 
				((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
				.getRequest();
		if (currentRequest == null) {
			throw new RuntimeException("Repository must be accessed within a request context");
		} 
		return repos.getRepository(currentRequest);
	}
	
	protected Node parse(final String markdownText) {
		return flexmark.getParser().parse(markdownText);
	}

	protected String render(final Node descriptionDocument) {
		return flexmark.getRenderer().render(descriptionDocument);
	}
	
	protected String render(final String markdownText) {
		return render(parse(markdownText));
	}

}
