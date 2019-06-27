package com.gratchev.mizoine.repository;

import static com.gratchev.mizoine.repository.RepositoryUtils.uriEncodePath;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class RepositoryVerifyer {
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryVerifyer.class);
	private final DateFormat FILE_DF = new SimpleDateFormat(" yyyy-MM-dd HH:mm:ss");
	private final ObjectMapper objectMapper;

	final Writer logWriter;
	final String rootPath;

	RepositoryVerifyer(final Writer logWriter, final File root, final ObjectMapper objectMapper) throws IOException {
		this.logWriter = logWriter;
		this.objectMapper = objectMapper;
		rootPath = root.getAbsolutePath();
		log("Repository check: " + rootPath);
	}

	String edit(final File file) {
		final String path = file.getAbsolutePath();
		final String date = file.exists() ? FILE_DF.format(new Date(file.lastModified())) : " -";
		if (!path.startsWith(rootPath)) {
			return path + date;
		}
		
		String subPath = path.substring(rootPath.length());
		if (subPath.startsWith(File.separator)) {
			subPath = subPath.substring(1);
		}
		
		final String uri = uriEncodePath(subPath);
		return 
				"[" + subPath + "](edit/" +  uri + " \"" + path + "\") "
				+ ( 
					file.exists() ? (
						"[download](" + Repository.RESOURCE_URI_BASE
						+ uri + " \"" + path + "\")"
						+ " bytes: " + file.length() + ", " + date
					) : ( " create" )
				)
				;
	}
	
	String lnk(final File file) {
		final String path = file.getAbsolutePath();
		final String date = file.exists() ? FILE_DF.format(new Date(file.lastModified())) : " -";
		if (!path.startsWith(rootPath)) {
			return path + date;
		}
		
		String subPath = path.substring(rootPath.length());
		if (subPath.startsWith(File.separator)) {
			subPath = subPath.substring(1);
		}
		
		if (!file.isFile()) {
			return subPath + date;
		} else {
			return 
					"[" + subPath + "](" + Repository.RESOURCE_URI_BASE
					+ uriEncodePath(subPath) + " \"" + path + "\")"
					+ " bytes: " + file.length() + ", " + date;
		}
	}

	void log(final String text) throws IOException {
		LOGGER.debug(text);
		logWriter.append(text);
		logWriter.append('\n');
	}

	void good(final String text) throws IOException {
		LOGGER.info(text);
		logWriter.append(":/: ");
		logWriter.append(text);
		logWriter.append("\n");
	}

	void warn(final String text) throws IOException {
		LOGGER.warn(text);
		logWriter.append(":!: ");
		logWriter.append(text);
		logWriter.append("\n");
	}

	void err(final String text) throws IOException {
		LOGGER.error(text);
		logWriter.append("<div class=\"alert alert-danger\">");
		logWriter.append(text);
		logWriter.append("</div>\n\n");
	}

	boolean dirExists(final String readableName, final File dir) throws IOException {
		if (dir.exists()) {
			if (!dir.isDirectory()) {
				err(readableName + " is not a directory: " + lnk(dir));
				return false;
			}
			good(readableName + " exists: " + lnk(dir));
			return true;
		} else {
			warn(readableName + " doesn't exist: " + lnk(dir));
		}
		return false;
	}

	boolean fileExists(final String readableName, final File file) throws IOException {
		if (file.exists()) {
			if (!file.isFile()) {
				err(readableName + " is not a file: " + lnk(file));
				return false;
			}
			good(readableName + " exists: " + edit(file));
			return true;
		} else {
			warn(readableName + " doesn't exist: " + edit(file));
		}
		return false;
	}

	boolean checkMeta(final File dir) throws IOException {
		if (!dir.exists()) {
			warn("Directory missing: " + lnk(dir));
			return false;
		}
		if (!dir.isDirectory()) {
			err("Not a directory: " + lnk(dir));
			return false;
		}
		fileExists("Description", new File(dir, Repository.DESCRIPTION_MD_FILENAME));
		final File metaFile = new File(dir, Repository.META_JSON_FILENAME);
		if (fileExists("Meta", metaFile)) {
			try {
				final JsonNode node = objectMapper.readTree(metaFile);
				final JsonNode titleNode = node.get("title");
				if (titleNode != null) {
					log("##### " + titleNode.asText());
				} else {
					warn("Title not found");
				}
				
			} catch (JsonParseException je) {
				err("Parsing problem: " + je.getMessage());
			}
		}
		
		return true;
	}

}