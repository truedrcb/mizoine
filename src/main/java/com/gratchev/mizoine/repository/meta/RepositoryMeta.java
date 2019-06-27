package com.gratchev.mizoine.repository.meta;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class RepositoryMeta extends BaseMeta {
	@JsonInclude(Include.NON_NULL)
	public static class TagMeta {
		public String icon;
		public String badgeStyle;
		public Set<String> synonyms;

		@Override
		public String toString() {
			return "TagMeta [icon=" + icon + ", badgeStyle=" + badgeStyle + ", synonyms=" + synonyms + "]";
		}
	}
	
	public Map<String, TagMeta> tags;
	public Map<String, TagMeta> statuses;

	@Override
	public String toString() {
		return "RepositoryMeta [tags=" + tags + ", title=" + title + ", creationDate=" + creationDate + ", creator="
				+ creator + "]";
	}
}
