package com.gratchev.mizoine.repository.meta;

import static com.gratchev.utils.StringUtils.isUnsignedInteger;

import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class IssueMeta extends BaseMeta {
	public static final Comparator<String> BY_ISSUE_NUMBER_DESCENDING = (s1, s2) -> {
		if(isUnsignedInteger(s1)) {
			if(isUnsignedInteger(s2)) {
				int i1 = Integer.parseInt(s1);
				int i2 = Integer.parseInt(s2);
				if (i1 != i2) {
					return i2 - i1; 
				}
			} else {
				return 1;
			}
		} else {
			if(isUnsignedInteger(s2)) {
				return -1; 
			}
		}
		return -s1.compareTo(s2);
	};

	public List<CommentMeta> comments;
	public List<AttachmentMeta> attachments;
}
