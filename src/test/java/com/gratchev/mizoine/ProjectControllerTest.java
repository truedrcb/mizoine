package com.gratchev.mizoine;

import static com.gratchev.mizoine.api.ProjectApiController.BY_ISSUE_NUMBER_DESCENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.repository.Issue;

public class ProjectControllerTest {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ProjectControllerTest.class);
	
	private static Issue i(final String issueNumber) {
		final Issue issue = new Issue();
		issue.issueNumber = issueNumber;
		return issue;
	}

	private static int compare(final String issueNumber1, final String issueNumber2) {
		return BY_ISSUE_NUMBER_DESCENDING.compare(i(issueNumber1), i(issueNumber2));
	}
	
	private static void assertOrder(final String issueNumber1, final String issueNumber2) {
		assertThat(compare(issueNumber2, issueNumber1)).as("%s must come after %s", issueNumber2, issueNumber1).isLessThan(0);
		assertThat(compare(issueNumber1, issueNumber2)).as("%s must come before %s", issueNumber1, issueNumber2).isGreaterThan(0);
	}
	
	@Test
	public void assert_BY_ISSUE_NUMBER_DESCENDING_comparator() {
		assertEquals(0, compare("0", "0"));
		assertEquals(0, compare("ABc", "ABc"));
		assertOrder("0001", "1");
		assertOrder("003", "03");
		assertOrder("0", "1");
		assertOrder("2", "10");
		assertOrder("1", "a");
		assertOrder("2", "2a");
		assertOrder("c", "d");
		assertOrder("e", "ee");
		assertOrder("603", "10000");
		assertOrder("0003", "05");
		assertOrder("04", "0005");
	}
}
