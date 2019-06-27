package com.gratchev.mizoine.repository.meta;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public class AttachmentMeta extends BaseMeta {
	
	public String fileName;
	
	@JsonInclude(Include.NON_NULL)
	public static class UploadMeta {
		public String originalFileName;
		public String name;
		public String contentType;
		public long size;

		@Override
		public String toString() {
			return "UploadMeta [originalFileName=" + originalFileName + ", name=" + name + ", contentType="
					+ contentType + ", size=" + size + "]";
		}
	}
	
	public UploadMeta upload;
	
	@Override
	public String toString() {
		return "AttachmentMeta [fileName=" + fileName + ", upload=" + upload + "]";
	}

}
