package com.gratchev.mizoine.repository.meta;

import java.util.Map;
import java.util.TreeMap;

public class ProjectMeta extends BaseMeta {
	public String icon;
	public String badgeStyle;
	public Map<String, IssueMeta> issues;

	public void putIssue(final String issueNumber, final IssueMeta meta) {
		if (issues == null) {
			issues = new TreeMap<>(IssueMeta.BY_ISSUE_NUMBER_DESCENDING);
		}
		issues.put(issueNumber, meta);
	}
}
