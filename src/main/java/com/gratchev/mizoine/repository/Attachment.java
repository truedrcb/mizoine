package com.gratchev.mizoine.repository;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.gratchev.mizoine.repository.meta.AttachmentMeta;

@JsonInclude(Include.NON_NULL)
public class Attachment extends BaseEntityInfo<AttachmentMeta> {
	@Override
	public String toString() {
		return "Attachment [shortId=" + id + ", meta=" + meta + ", files=" + files + ", thumbnails=" + thumbnails
				+ ", previews=" + previews + "]";
	}
	
	public static class FileInfo {
		@Override
		public String toString() {
			return "FileInfo [fileName=" + fileName + ", fullFileUri=" + fullFileUri + "]";
		}
		public String fileName;
		public String fullFileUri;
	}
	
	public ArrayList<FileInfo> files;
	public ArrayList<FileInfo> thumbnails;
	public ArrayList<FileInfo> previews;
	public FileInfo thumbnail;
	public FileInfo preview;
	
	public String getTitle() {
		if (meta != null) {
			if (meta.title != null) {
				return meta.title;
			}
		}
		
		if (files != null) {
			if (files.size() > 0) {
				final String fileName = files.get(0).fileName;
				if (fileName != null) {
					return fileName;
				}
			}
		}
		return "";
	}
}
