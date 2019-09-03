package com.gratchev.mizoine.repository.meta;

import static com.gratchev.utils.StringUtils.isUnsignedInteger;

import java.util.Collection;
import java.util.Comparator;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.collect.TreeMultiset;
import com.gratchev.mizoine.repository.Attachment;
import com.gratchev.mizoine.repository.BaseEntityInfo;
import com.gratchev.mizoine.repository.Comment;

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

	@JsonInclude(Include.NON_NULL)
	public static class Ment {
		public Comment comment;
		public Attachment attachment;
		public BaseEntityInfo<? extends BaseMeta> ment() {
			if (comment != null) {
				return comment;
			} else {
				return attachment;
			}
		}
	}

	public static final Comparator<Ment> MENTS_COMPARATOR = new Comparator<Ment>() {
		private Date getDate(final Ment o) {
			if (o == null) return null;
			final BaseEntityInfo<? extends BaseMeta> ment = o.ment();
			if (ment == null) return null;
			final BaseMeta meta = ment.meta;
			if (meta == null) return null;
			return meta.creationDate;
		}
	
		private String getId(final Ment o) {
			if (o == null) return null;
			final BaseEntityInfo<? extends BaseMeta> ment = o.ment();
			if (ment == null) return null;
			return ment.id;
		}
		/**
		 * Compare by creation date if possible. Otherwise by id (if possible).<br>
		 * Objects come in following order:
		 * <ol>
		 * <li>Both dates present: Greatest date (most recent)</li>
		 * <li>Only one date present: With date first</li>
		 * <li>Both dates missing: Both ids present: Greatest id</li>
		 * <li>Both dates missing: One id present: With id first</li>
		 * <li>Both dates missing: Both ids missing: Incomparable, equal</li>
		 * </ol>
		 * 
		 */
		@Override
		public int compare(final Ment o1, final Ment o2) {
			final Date d1 = getDate(o1);
			final Date d2 = getDate(o2);
			if (d1 != null && d2 != null) {
				return -d1.compareTo(d2);
			}
			if (d1 != null) {
				return -1;
			}
			if (d2 != null) {
				return 1;
			}
			final String id1 = getId(o1);
			final String id2 = getId(o2);
			if (id1 != null && id2 != null) {
				return -id1.compareTo(id2);
			}
			if (id1 != null) {
				return -1;
			}
			if (id2 != null) {
				return 1;
			}
			return 0;
		}
	};

	public Collection<Ment> ments;

	private Collection<Ment> getMents() {
		if (ments == null) {
			ments = TreeMultiset.create(MENTS_COMPARATOR);
		}
		return ments;
	}
	
	public Attachment putAttachment(final String id, final AttachmentMeta meta) {
		getMents();
		final Attachment attachment = new Attachment();
		attachment.id = id;
		attachment.meta = meta;
		final Ment ment = new Ment();
		ment.attachment = attachment;
		ments.add(ment);
		return attachment;
	}

	public Comment putComment(final String id, final CommentMeta meta) {
		getMents();
		final Ment ment = new Ment();
		ment.comment = new Comment();
		ment.comment.id = id;
		ment.comment.meta = meta;
		ments.add(ment);
		return ment.comment;
	}
}
