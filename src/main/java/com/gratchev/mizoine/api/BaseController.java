package com.gratchev.mizoine.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.gratchev.mizoine.FlexmarkComponent;
import com.gratchev.mizoine.SignedInUser;
import com.gratchev.mizoine.repository.Repository;
import com.vladsch.flexmark.util.ast.Node;

public abstract class BaseController {
	private static final String DEFAULT_REPO_KEY = "";

	@Value("${repository.home:sample/test_repo1}")
	protected String defaultRootPath;
	@Autowired
	protected FlexmarkComponent flexmark;
	@Autowired
	protected SignedInUser currentUser;

	private Repository defaultRepo;

	public Repository getRepo() {
		if (defaultRepo == null) {
			defaultRepo = new Repository(defaultRootPath, DEFAULT_REPO_KEY);
		}
		return defaultRepo;
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
