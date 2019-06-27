package com.gratchev.mizoine.repository.meta;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class CommentMeta extends BaseMeta {
	/**
	 * If imported from e-mail. Reference Id.
	 */
	public String messageId;
	
	/**
	 * If imported from e-mail. Mail headers.
	 */
	public Map<String, String> messageHeaders;

	@Override
	public String toString() {
		return "CommentMeta [messageId=" + messageId + ", messageHeaders=" + messageHeaders + ", title=" + title
				+ ", creationDate=" + creationDate + ", creator=" + creator + "]";
	}
}
