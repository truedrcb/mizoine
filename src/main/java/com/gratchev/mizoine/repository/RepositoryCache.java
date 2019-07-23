package com.gratchev.mizoine.repository;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.gratchev.mizoine.repository.Repository.Proxy;
import com.gratchev.mizoine.repository.meta.BaseMeta;
import com.gratchev.mizoine.repository.meta.ProjectMeta;
import com.gratchev.mizoine.repository.meta.RepositoryMeta;
import com.gratchev.mizoine.repository.meta.RepositoryMeta.TagMeta;;

public class RepositoryCache {
	
	final Repository repo;
	
	final Map<String, Project> projects = new HashMap<>();
	final Map<String, Issue> issues = new HashMap<>();
	final Map<String, TagMeta> tags = new HashMap<>();
	final Set<String> statuses = new HashSet<>();
	
	RepositoryMeta repositoryMeta;
	
	public RepositoryCache(final Repository repo) {
		this.repo = repo;
	}
	
	public Project getProject(final String projectId) {
		Project project = projects.get(projectId);
		if (project == null) {
			getRepositoryMeta();
			project = repo.readProjectInfo(projectId);
			if (project == null) {
				project = new Project();
			}
			if (project.meta == null) {
				project.meta = new ProjectMeta();
			}
			projects.put(projectId, project);
			repositoryMeta.putProject(projectId, project.meta);
		}
		return project;
	}

	public Issue getIssue(final String projectId, final String issueNumber) {
		final String issueKey = projectId + "-" + issueNumber;
		Issue issue = issues.get(issueKey);
		if (issue == null) {
			issue = repo.issue(projectId, issueNumber).readInfo();
			issues.put(issueKey, issue);

			final Project project = getProject(projectId);
			project.meta.putIssue(issueNumber, issue.meta);
		}
		
		return issue;
	}
	
	public Attachment getAttachment(final String projectId, final String issueNumber, final String attachmentId) {
		final Issue issue = getIssue(projectId, issueNumber);
		return issue.meta.putAttachment(attachmentId, repo.attachment(projectId, issueNumber, attachmentId).readMeta());
	}

	public Comment getComment(final String projectId, final String issueNumber, final String commentId) {
		final Issue issue = getIssue(projectId, issueNumber);
		return issue.meta.putComment(commentId, repo.comment(projectId, issueNumber, commentId).readMeta());
	}

	private void addAllTagMetas(final Map<String, TagMeta> newTags) {
		for (final Entry<String, TagMeta> e : newTags.entrySet()) {
			final TagMeta tag = e.getValue();
			tags.put(e.getKey(), tag);
			if (tag.synonyms != null) {
				for (final String synonym : tag.synonyms) {
					tags.put(synonym, tag);
				}
			}
		}
	}

	private void addAllStatuses(final Map<String, TagMeta> newTags) {
		for (final Entry<String, TagMeta> e : newTags.entrySet()) {
			final TagMeta tag = e.getValue();
			statuses.add(e.getKey());
			if (tag.synonyms != null) {
				for (final String synonym : tag.synonyms) {
					statuses.add(synonym);
				}
			}
		}
	}

	public RepositoryMeta getRepositoryMeta() {
		if (repositoryMeta == null) {
			repositoryMeta = repo.readRepositoryMeta();
			if (repositoryMeta == null) {
				repositoryMeta = new RepositoryMeta();
			} else {
				if (repositoryMeta.tags != null) {
					addAllTagMetas(repositoryMeta.tags);
				}
				if (repositoryMeta.statuses != null) {
					addAllTagMetas(repositoryMeta.statuses);
					addAllStatuses(repositoryMeta.statuses);
				}
			}
		}
		return repositoryMeta;
	}
	
	
	public TagMeta getTagMeta(final String tagName) {
		getRepositoryMeta();
		
		return tags.get(tagName);
	}
	
	public boolean isStatus(final String tagName) {
		getRepositoryMeta();
		
		return statuses.contains(tagName);
	}
	
	
	public void addTagOrStatus(final String tagName, final Proxy<? extends BaseMeta> proxy) throws IOException {
		if (isStatus(tagName)) {
			proxy.addStatus(tagName);
		} else {
			proxy.addTags(tagName);
		}
	}

}
