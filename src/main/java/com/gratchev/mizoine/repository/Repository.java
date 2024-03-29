package com.gratchev.mizoine.repository;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.gratchev.mizoine.GitComponent;
import com.gratchev.mizoine.ShortIdGenerator;
import com.gratchev.mizoine.SignedInUser;
import com.gratchev.mizoine.preview.AttachmentPreviewGenerator;
import com.gratchev.mizoine.preview.ImagePreviewGenerator;
import com.gratchev.mizoine.preview.PdfPreviewGenerator;
import com.gratchev.mizoine.preview.SvgPreviewGenerator;
import com.gratchev.mizoine.repository.Attachment.FileInfo;
import com.gratchev.mizoine.repository.meta.*;
import com.gratchev.mizoine.repository2.file.RepositoryConstants;
import com.gratchev.utils.FileNameDateParser;
import com.gratchev.utils.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.text.ParseException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;

import static com.google.common.io.Files.getFileExtension;
import static com.gratchev.mizoine.preview.AttachmentPreviewGenerator.*;
import static com.gratchev.mizoine.repository.RepositoryUtils.*;
import static com.gratchev.utils.StringUtils.isUnsignedInteger;

public class Repository {

	public static final String MIZOINE_DIR = ".mizoine";

	public static final String RESOURCE_URI_BASE = "/attachments/";

	private static final Logger LOGGER = LoggerFactory.getLogger(Repository.class);

	private static final String VERIFICATION_LOG = "repository-verification-log.md";

	private static final String ATTACHMENTS_DIRNAME = "attachments";

	private static final String META_DIRNAME = "meta";

	private static final String TAGS_DIRNAME = "tags";

	private static final String STATUS_DIRNAME = "status";

	private static final String COMMENTS_DIRNAME = "comments";

	private static final String ISSUES_DIRNAME = "issues";

	private static final String PROJECTS_DIRNAME = "projects";

	public static final String DESCRIPTION_MD_FILENAME = "description.md";

	public static final String META_JSON_FILENAME = RepositoryConstants.META_JSON_FILENAME;

	public static final String ATTACHMENT_COPY_FILENAME_WO_EXT = "original";

	private final ObjectMapper objectMapper;
	private final ShortIdGenerator shortIdGenerator = new ShortIdGenerator();

	static final Comparator<? super Attachment> ATTACHMENTS_BY_DATE_NAME = (a1, a2) -> {
		if (a1.meta != null && a2.meta != null && a1.meta.creationDate != null && a2.meta.creationDate != null) {
			// Sort by date descending (if available)
			return a2.meta.creationDate.compareTo(a1.meta.creationDate);
		}

		// If dates are not available: sort by name ascending
		final String title1 = a1.getTitle();
		final String title2 = a2.getTitle();
		return title1.compareTo(title2);
	};

	static final Comparator<? super FileInfo> ATTACHMENT_FILES_BY_INDEX = (o1, o2) -> {
		if (o1.fileName == null || o2.fileName == null) {
			return 0;
		}

		int lastIdenticalChar = 0;
		for (; ; ) {
			if (lastIdenticalChar >= o1.fileName.length()) {
				break;
			}
			if (lastIdenticalChar >= o2.fileName.length()) {
				break;
			}
			final char c1 = o1.fileName.charAt(lastIdenticalChar);
			if ((c1 >= '0' && c1 <= '9') || c1 != o2.fileName.charAt(lastIdenticalChar)) {
				break;
			}
			lastIdenticalChar++;
		}

		final String s1 = o1.fileName.substring(lastIdenticalChar);
		final String s2 = o2.fileName.substring(lastIdenticalChar);

		final String ns1 = s1.replaceFirst("[^0-9].*", ""); // TODO: OMG Refactor this
		final String ns2 = s2.replaceFirst("[^0-9].*", "");

		if (isUnsignedInteger(ns1)) {
			if (isUnsignedInteger(ns2)) {
				int i1 = Integer.parseInt(ns1);
				int i2 = Integer.parseInt(ns2);
				return i1 - i2;
			} else {
				return -1;
			}
		} else {
			if (isUnsignedInteger(ns2)) {
				return 1;
			}
		}
		return s1.compareTo(s2);
	};

	private final String rootPath;

	private final String resourceUriPrefix;

	protected final GitComponent git;

	@Deprecated
	public Repository(final String rootPath, final String id) {
		this.rootPath = rootPath;
		this.resourceUriPrefix = id.length() > 0 ? (id + '/') : "";
		this.git = new GitComponent(this);

		// https://github.com/FasterXML/jackson-core/wiki/JsonParser-Features
		final JsonFactory jsonFactory = new JsonFactory();
		jsonFactory.enable(JsonParser.Feature.ALLOW_COMMENTS);

		objectMapper = new ObjectMapper(jsonFactory);

		objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		final DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
		pp.indentObjectsWith(new DefaultIndenter("\t", "\n"));
		objectMapper.setDefaultPrettyPrinter(pp);

		LOGGER.info("\r\nMizoine\r\nRepository initialized for path: " + getRoot().getAbsolutePath() + "\r\n");
	}

	public GitComponent getGitComponent() {
		return git;
	}

	public File getRoot() {
		return new File(rootPath);
	}

	public File getMetaFile() {
		return new File(rootPath, META_JSON_FILENAME);
	}

	public File getRootMizoineDir() {
		return new File(getRoot(), MIZOINE_DIR);
	}

	public File getProjectsRoot() {
		return new File(getRoot(), PROJECTS_DIRNAME);
	}

	public File getProjectRoot(final String project) {
		return new File(getProjectsRoot(), project);
	}

	public File getProjectDescriptionFile(final String project) {
		return new File(getProjectRoot(project), DESCRIPTION_MD_FILENAME);
	}

	public String readProjectDescription(final String project) {
		return readDescription(getProjectRoot(project));
	}

	public File getLuceneDir() {
		return new File(getRootMizoineDir(), ".lucene");
	}

	public File getProjectIssuesRoot(final String project) {
		return new File(getProjectRoot(project), ISSUES_DIRNAME);
	}

	public File getIssueRoot(final String project, final String issueNumber) {
		return new File(getProjectIssuesRoot(project), issueNumber);
	}

	public File getIssueCommentsDir(final String project, final String issueNumber) {
		return new File(getIssueRoot(project, issueNumber), COMMENTS_DIRNAME);
	}

	public File getCommentRoot(final String project, final String issueNumber, final String commentId) {
		return new File(getIssueCommentsDir(project, issueNumber), commentId);
	}

	public ProjectMeta readProjectMeta(final String project) {
		return readMeta(getProjectRoot(project), ProjectMeta.class);
	}

	public IssueMeta readIssueMeta(final String project, final String issueNumber) {
		return readMeta(getIssueRoot(project, issueNumber), IssueMeta.class);
	}

	public CommentMeta readCommentMeta(final String project, final String issueNumber, final String commentId) {
		return readMeta(getCommentRoot(project, issueNumber, commentId), CommentMeta.class);
	}

	public Set<String> readTags(final File baseDir, final String tagsDirName) {
		final File tagsDir = new File(baseDir, tagsDirName);

		if (tagsDir.exists() && tagsDir.isDirectory()) {
			final Set<String> tags = new TreeSet<>();
			for (final File file : tagsDir.listFiles()) {
				if (file.isFile() && !file.isHidden()) {
					tags.add(file.getName());
				}
			}
			return tags;
		}

		return null;
	}

	public Set<String> readTags(final File baseDir) {
		return readTags(baseDir, TAGS_DIRNAME);
	}

	public Set<String> readStatus(final File baseDir) {
		return readTags(baseDir, STATUS_DIRNAME);
	}

	private void addTags(final File baseDir, final String tagsDirName, final String... tags) throws IOException {
		final File tagsDir = new File(baseDir, tagsDirName);

		checkOrCreateDirectory(tagsDir);

		for (final String tag : tags) {
			if (tag == null || tag.length() <= 0) {
				LOGGER.debug("Empty tag creation skipped");
			}
			final File tagFile = new File(tagsDir, tag);
			try {
				tagFile.createNewFile();
			} catch (IOException e) {
				LOGGER.error("Unable to create tag: {}", tag, e);
			}
		}
	}

	private void removeTags(final File baseDir, final String tagsDirName, final String... tags) {
		final File tagsDir = new File(baseDir, tagsDirName);

		if (!tagsDir.exists()) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Skip removing tags from non-existing dir: " + tagsDir.getAbsolutePath());
			}
			return;
		}

		for (final String tag : tags) {
			if (tag == null || tag.length() <= 0) {
				LOGGER.debug("Empty tag deleting skipped");
			}
			final File tagFile = new File(tagsDir, tag);
			if (!tagFile.exists()) {
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Tag is already removed: " + tag);
				}
				continue;
			}
			try {
				if (!tagFile.delete()) {
					LOGGER.error("Unable to remove tag: {}", tag);
				}
			} catch (final Exception e) {
				LOGGER.error("Unable to remove tag: {}", tag, e);
			}
		}
	}

	private <T> T readMeta(final File baseDir, final Class<T> valueType) {
		// read meta
		final File metaFile = new File(baseDir, META_JSON_FILENAME);
		if (metaFile.exists()) {
			try {
				final T meta = objectMapper.readValue(metaFile, valueType);
				if (LOGGER.isTraceEnabled()) {
					LOGGER.trace("Meta file: " + metaFile + " contains " + meta);
				}
				return meta;
			} catch (IOException e) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug(e.getMessage(), e);
				}
				return null;
			}
		}
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Meta file missing: " + metaFile);
		}

		return null;
	}

	private String readDescription(final File baseDir) {
		final File descriptionFile = new File(baseDir, DESCRIPTION_MD_FILENAME);
		if (descriptionFile.exists()) {
			try {
				final String description = FileUtils.readTextFile(descriptionFile);
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug("Description file: " + descriptionFile + " contains " + description);
				}
				return description;
			} catch (IOException e) {
				if (LOGGER.isDebugEnabled()) {
					LOGGER.debug(e.getMessage(), e);
				}
				return null;
			}
		}

		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Description file missing: " + descriptionFile);
		}
		return null;

	}

	private <T> void writeMetaMeta(final File baseDir, final T value) throws IOException {
		final File metaDir = new File(baseDir, META_DIRNAME);
		checkOrCreateDirectory(metaDir);
		writeMeta(metaDir, value);
	}

	private <T> void writeMeta(final File baseDir, final T value) throws IOException {
		final File metaFile = new File(baseDir, META_JSON_FILENAME);
		if (metaFile.exists()) {
			LOGGER.info("Metadata already exists: " + metaFile.getAbsolutePath());
		}
		objectMapper.writeValue(metaFile, value);
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Meta uploaded: " + value + " to file: " + metaFile.getAbsolutePath());
		}
	}

	private void writeDescription(final File baseDir, final String description) throws IOException {
		writeFile(baseDir, DESCRIPTION_MD_FILENAME, description);
	}

	private void writeFile(final File baseDir, final String fileName, final String content) throws IOException {
		final File file = new File(baseDir, fileName);
		if (file.exists()) {
			LOGGER.info("File already exists: " + file.getAbsolutePath());
		}
		FileUtils.overwriteTextFile(content, file);
	}

	private ArrayList<String> readAttachmentFileNames(final File baseDir) {
		final ArrayList<String> attachmentFileNames = new ArrayList<>();

		for (final File file : baseDir.listFiles()) {
			if (file.isDirectory()) {
				// skip unexpected dirs
				continue;
			}
			final String fileName = file.getName();
			if (META_JSON_FILENAME.equals(fileName) || DESCRIPTION_MD_FILENAME.equals(fileName)) {
				// skip known meta file
				continue;
			}

			attachmentFileNames.add(fileName);
		}

		return attachmentFileNames;
	}

	public String getResourceUriBase() {
		return RESOURCE_URI_BASE + resourceUriPrefix;
	}

	private String attachmentUriBase(final String project, final String issueNumber, final String shortId) {
		return getResourceUriBase() + PROJECTS_DIRNAME + '/' + project + '/' + ISSUES_DIRNAME + '/' + issueNumber + '/'
				+ ATTACHMENTS_DIRNAME + '/' + shortId + '/';
	}

	synchronized private File createAttachmentFolder(final File attachmentsDir, final ZonedDateTime creationDate) throws IOException {
		final Set<String> existingFiles = new TreeSet<>(Arrays.asList(attachmentsDir.list()));
		LOGGER.debug("Already existing ids: " + existingFiles);

		final String shortId = shortIdGenerator.createId(creationDate, existingFiles);

		LOGGER.debug("New id: {} generated from date {}", shortId, creationDate);

		final File attachmentFolder = new File(attachmentsDir, shortId);
		if (!attachmentFolder.mkdir()) {
			throw new IOException("Unable to create directory: " + attachmentFolder.getAbsolutePath());
		}

		return attachmentFolder;
	}

	public List<Project> getProjects() {
		final ArrayList<Project> projects = new ArrayList<>();
		final File[] listFiles = getProjectsRoot().listFiles();
		if (listFiles != null) {
			for (final File file : listFiles) {
				if (!file.isDirectory() || file.isHidden()) {
					continue;
				}

				final Project project = new Project();
				projects.add(project);

				project.project = file.getName();
				project.meta = readMeta(file, ProjectMeta.class);
			}
		}

		return projects;
	}

	public RepositoryMeta readRepositoryMeta() {
		return readMeta(getRoot(), RepositoryMeta.class);
	}

	public Date extractDateFromFilename(final String fileName) {
		final RepositoryMeta meta = readRepositoryMeta();
		if (meta == null || meta.uploadFilenameDateTemplates == null || meta.uploadFilenameDateTemplates.size() < 1) {
			return null;
		}
		final FileNameDateParser parser = new FileNameDateParser();
		for (final String template : meta.uploadFilenameDateTemplates) {
			parser.addTemplate(template);
		}
		try {
			return parser.parse(fileName);
		} catch (final ParseException e) {
			LOGGER.error("Unable to extract date from file name: {}", fileName, e);
			return null;
		}
	}

	public Project readProjectInfo(final String project) {
		return readProjectInfo(getProjectRoot(project));
	}

	public Project readProjectInfo(final File projectDir) {
		final Project project = new Project();
		project.project = projectDir.getName();
		project.meta = readMeta(projectDir, ProjectMeta.class);
		project.tags = readTags(projectDir);
		project.status = readStatus(projectDir);
		return project;
	}

	public List<Issue> getIssues(final String project) {
		final ArrayList<Issue> issues = new ArrayList<>();
		final File projectIssuesRoot = getProjectIssuesRoot(project);
		if (projectIssuesRoot.exists()) {
			for (final File file : projectIssuesRoot.listFiles()) {
				if (!file.isDirectory() || file.isHidden()) {
					continue;
				}
				final Issue issue = issue(file).readInfo();

				issues.add(issue);
			}
		}
		return issues;
	}

	public Issue createIssue(final File projectIssuesRoot, final String newIssueTitle, final String newIssueMarkdown,
							 final SignedInUser currentUser) throws IOException {
		final Issue issue = new Issue();
		issue.issueNumber = "0";
		issue.meta = new IssueMeta();
		LOGGER.info("New issue: " + newIssueTitle + "\r\n" + newIssueMarkdown);
		issue.meta.creationDate = new Date();
		issue.meta.creator = currentUser.getName();
		issue.meta.title = newIssueTitle;
		LOGGER.info("Creation date: " + issue.meta.creationDate);
		if (projectIssuesRoot.exists()) {
			LOGGER.info("Searching next issue number");
			int maxIssueNumber = 0;
			for (final File dir : projectIssuesRoot.listFiles()) {
				try {
					int i = Integer.parseInt(dir.getName());
					if (i > maxIssueNumber) {
						maxIssueNumber = i;
					}
				} catch (NumberFormatException ne) {
					// Skip not number
				}
			}
			LOGGER.info("Max issue number: " + maxIssueNumber + " in directory " + projectIssuesRoot.getAbsolutePath());
			issue.issueNumber = "" + (maxIssueNumber + 1);
		}
		LOGGER.info("New issue number: " + issue.issueNumber);
		final File issueDir = new File(projectIssuesRoot, issue.issueNumber);
		LOGGER.info("Creating folder for issue: " + issueDir.getAbsolutePath());
		createNewDirectory(issueDir);
		writeMeta(issueDir, issue.meta);
		if (newIssueMarkdown != null) {
			writeDescription(issueDir, newIssueMarkdown);
		}
		return issue;
	}

	public interface MetaUpdater<T extends BaseMeta> {
		void update(T meta) throws Exception;
	}

	private final AttachmentPreviewGenerator[] previewGenerators = {new PdfPreviewGenerator(),
			new SvgPreviewGenerator(), new ImagePreviewGenerator()};

	public File getVerificationLogFile() {
		final File mizoineDir = getRootMizoineDir();
		return new File(mizoineDir, VERIFICATION_LOG);
	}

	public interface Visitor {
		void project(String project) throws Exception;

		void issue(String project, String issueNumber) throws Exception;

		void attachment(String project, String issueNumber, String attachmentId) throws Exception;

		void comment(String project, String issueNumber, String commentId) throws Exception;

		void user(String user) throws Exception;
	}

	protected void verifyRepositoryAndGenerateLog(final RepositoryVerifyer v) throws IOException {
		if (!v.checkMeta(getRoot())) {
			return;
		}

		final File projectsRoot = getProjectsRoot();
		v.log("# Projects");
		if (v.dirExists("Projects root", projectsRoot)) {
			for (final File projectDir : projectsRoot.listFiles()) {
				final String project = projectDir.getName();
				v.log("<a id='project-" + project + "'></a>");
				v.log("## [" + project + "](/project/" + project + ")");
				if (!v.checkMeta(projectDir)) {
					continue;
				}
				final File issuesRoot = getProjectIssuesRoot(project);
				if (v.dirExists("Issues root", issuesRoot)) {
					v.log("<div class=\"jumbotron jumbotron-fluid px-5\">\n");
					for (final File issueDir : issuesRoot.listFiles()) {
						final String issueNumber = issueDir.getName();
						final String issueFullName = project + "-" + issueNumber;
						v.log("<a id='issue-" + issueFullName + "'></a>");
						v.log("### [" + issueFullName + "](/issue/" + issueFullName + ")");
						if (!v.checkMeta(issueDir)) {
							continue;
						}

						v.log("<div class=\"bg-light p-4\">\n");
						final File attachmentsDir = issue(issueDir).getAttachmentsDir();
						if (v.dirExists("Attachments dir", attachmentsDir)) {
							for (final File attachmentDir : attachmentsDir.listFiles()) {
								final String attachmentId = attachmentDir.getName();
								v.log("#### [" + attachmentId + "](/attachment/" + issueFullName + "/" + attachmentId
										+ ")");
								if (!v.dirExists("Attachment dir", attachmentDir)) {
									continue;
								}
								int count = 0;
								for (final File attachmentFile : attachmentDir.listFiles()) {
									if (!attachmentFile.isFile()) {
										continue;
									}
									v.log("File: " + v.lnk(attachmentFile));
									count++;
								}
								if (count == 1) {
									v.good("One attachment file found");
								} else {
									v.err("Cannot determine attachment file. Count: " + count);
								}
								final File metaDir = attachment(project, issueNumber, attachmentId).getMetaRoot();
								v.checkMeta(metaDir);
							}
						}
						v.log("</div>\n");

						v.log("<div class=\"bg-light mt-1 mb-5 p-4\">\n");
						final File commentsDir = getIssueCommentsDir(project, issueNumber);
						if (v.dirExists("Comments dir", commentsDir)) {
							for (final File commentDir : commentsDir.listFiles()) {
								final String commentId = commentDir.getName();
								v.log("#### [" + commentId + "](/comment/" + issueFullName + "/" + commentId + ")");
								if (!v.dirExists("Comment dir", commentDir)) {
									continue;
								}
								final File metaDir = getCommentRoot(project, issueNumber, commentId);
								v.checkMeta(metaDir);
							}
						}
						v.log("</div>\n");

					}
					v.log("</div>\n");
				}
			}
		}
	}

	protected void verifyRepositoryAndGenerateLog(final File logFile) throws IOException {
		try (final Writer w = new FileWriter(logFile)) {
			final RepositoryVerifyer v = new RepositoryVerifyer(w, getRoot(), objectMapper);
			try {
				verifyRepositoryAndGenerateLog(v);
			} finally {
				v.log("End.");
			}
		}
	}

	public void verifyRepositoryAndGenerateLog() throws IOException {
		final File mizoineDir = getRootMizoineDir();
		checkOrCreateHiddenDirectory(mizoineDir);

		final File logFile = new File(mizoineDir, VERIFICATION_LOG);
		LOGGER.info("Writing log to file: " + logFile.getAbsolutePath());

		verifyRepositoryAndGenerateLog(logFile);
	}

	void createInitialRepositoryDirectories() throws IOException {
		checkOrCreateDirectory(getProjectsRoot());
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public static class FilePathInfo {
		public String fileName;
		public String project;
		public String issueNumber;
		public String attachmentId;
		public String commentId;
		public String fullFileUri;

		public void clear() {
			fileName = null;
			project = null;
			issueNumber = null;
			attachmentId = null;
			commentId = null;
			fullFileUri = null;
		}
	}

	public FilePathInfo identifyFile(final Iterable<String> path, final FilePathInfo info) {
		info.clear();

		final Iterator<String> iterator = path.iterator();
		if (iterator.hasNext()) {
			if (PROJECTS_DIRNAME.equals(iterator.next())) {
				if (iterator.hasNext()) {
					info.project = iterator.next();
					if (iterator.hasNext()) {
						if (ISSUES_DIRNAME.equals(iterator.next())) {
							if (iterator.hasNext()) {
								info.issueNumber = iterator.next();
								if (iterator.hasNext()) {
									final String mentSubdir = iterator.next();
									if (iterator.hasNext()) {
										if (ATTACHMENTS_DIRNAME.equals(mentSubdir)) {
											info.attachmentId = iterator.next();
										} else if (COMMENTS_DIRNAME.equals(mentSubdir)) {
											info.commentId = iterator.next();
										}
									}
								}
							}
						}
					}
				}
			}
		}

		if (iterator.hasNext()) {
			info.fileName = iterator.next();
		}

		if (!iterator.hasNext() && info.attachmentId != null && info.fileName != null) {
			info.fullFileUri = uriEncodePath(
					attachmentUriBase(info.project, info.issueNumber, info.attachmentId) + info.fileName);
		}

		return info;
	}

	public void fullScan(final Visitor visitor) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Full scan: " + visitor);
		}

		final File projectsRoot = getProjectsRoot();
		if (projectsRoot.exists()) {
			for (final File projectDir : projectsRoot.listFiles()) {
				if (!projectDir.isDirectory() || projectDir.isHidden()) {
					continue;
				}
				final String project = projectDir.getName();
				try {
					visitor.project(project);
				} catch (Exception e) {
					LOGGER.error("project", e);
					continue;
				}
				final File issuesRoot = getProjectIssuesRoot(project);
				if (issuesRoot.exists()) {
					for (final File issueDir : issuesRoot.listFiles()) {
						if (!issueDir.isDirectory() || issueDir.isHidden()) {
							continue;
						}
						final String issueNumber = issueDir.getName();
						try {
							visitor.issue(project, issueNumber);
						} catch (Exception e) {
							LOGGER.error("issue", e);
							continue;
						}
						final File attachmentsDir = issue(issueDir).getAttachmentsDir();
						if (attachmentsDir.exists()) {
							for (final File attachmentDir : attachmentsDir.listFiles()) {
								if (!attachmentDir.isDirectory() || attachmentDir.isHidden()) {
									continue;
								}
								final String attachmentId = attachmentDir.getName();
								try {
									visitor.attachment(project, issueNumber, attachmentId);
								} catch (Exception e) {
									LOGGER.error("attachment", e);
								}
							}
						}
						final File commentsDir = getIssueCommentsDir(project, issueNumber);
						if (commentsDir.exists()) {
							for (final File commentDir : commentsDir.listFiles()) {
								if (!commentDir.isDirectory() || commentDir.isHidden()) {
									continue;
								}
								final String commentId = commentDir.getName();
								try {
									visitor.comment(project, issueNumber, commentId);
								} catch (Exception e) {
									LOGGER.error("comment", e);
								}
							}
						}
					}
				}
			}
		}
	}

	public abstract class Proxy<T extends BaseMeta> {
		abstract protected File getRoot();

		abstract protected Class<T> valueType();

		protected File getMetaRoot() {
			return getRoot();
		}

		public Set<String> readTags() {
			return Repository.this.readTags(getRoot(), TAGS_DIRNAME);
		}

		public Proxy<T> addTags(final String... tags) throws IOException {
			Repository.this.addTags(getRoot(), TAGS_DIRNAME, tags);
			return this;
		}

		public Proxy<T> removeTags(final String... tags) throws IOException {
			Repository.this.removeTags(getRoot(), TAGS_DIRNAME, tags);
			return this;
		}

		public Set<String> readStatus() {
			return Repository.this.readTags(getRoot(), STATUS_DIRNAME);
		}

		public Proxy<T> addStatus(final String... tags) throws IOException {
			Repository.this.addTags(getRoot(), STATUS_DIRNAME, tags);
			return this;
		}

		public Proxy<T> removeStatus(final String... tags) throws IOException {
			Repository.this.removeTags(getRoot(), STATUS_DIRNAME, tags);
			return this;
		}

		public T readMeta() {
			return Repository.this.readMeta(getMetaRoot(), valueType());
		}

		public T updateMeta(final MetaUpdater<T> updater, final SignedInUser currentUser) throws IOException {
			final File metaRoot = getMetaRoot();
			T meta = readMeta();
			if (meta == null) {
				try {
					meta = valueType().getDeclaredConstructor().newInstance();
				} catch (InstantiationException | IllegalAccessException | NoSuchMethodException
						| InvocationTargetException e) {
					LOGGER.error("Unexpected", e);
					throw new RuntimeException(e);
				}
				meta.creationDate = new Date();
				meta.creator = currentUser.getName();
			}

			try {
				updater.update(meta);
				writeMeta(metaRoot, meta);
			} catch (final Exception e) {
				LOGGER.error("Meta update failed", e);
				throw new IOException(e);
			}
			return meta;
		}

		public File getDescriptionFile() {
			return new File(getMetaRoot(), DESCRIPTION_MD_FILENAME);
		}

		public String readDescription() {
			return Repository.this.readDescription(getMetaRoot());
		}

		public void writeDescription(final String description) throws IOException {
			Repository.this.writeDescription(getMetaRoot(), description);
		}

		public void createDirs() throws IOException {
			checkOrCreateDirectory(getMetaRoot());
		}

		public void delete() throws IOException {
			FileUtils.removeDirectory(getRoot());
		}

	}

	public class ProjectProxy extends Proxy<ProjectMeta> {
		final File rootDir;
		final String project;

		private ProjectProxy(final String project) {
			this.project = project;
			this.rootDir = getProjectRoot(project);
		}

		private ProjectProxy(final File rootDir) {
			this.rootDir = rootDir;
			this.project = rootDir.getName();
		}

		@Override
		protected File getRoot() {
			return rootDir;
		}

		@Override
		protected Class<ProjectMeta> valueType() {
			return ProjectMeta.class;
		}

	}

	public ProjectProxy project(final String project) {
		return new ProjectProxy(project);
	}

	public interface FileSaver {
		void saveTo(final File attachmentFile) throws Exception;
	}

	public class IssueProxy extends Proxy<IssueMeta> {
		final File rootDir;
		final String project;
		final String issueNumber;

		private IssueProxy(final String project, final String issueNumber) {
			this.project = project;
			this.issueNumber = issueNumber;
			this.rootDir = getIssueRoot(project, issueNumber);
		}

		private IssueProxy(final File rootDir) {
			this.rootDir = rootDir;
			this.project = rootDir.getParentFile().getParentFile().getName();
			this.issueNumber = rootDir.getName();
		}

		@Override
		protected File getRoot() {
			return rootDir;
		}

		@Override
		protected Class<IssueMeta> valueType() {
			return IssueMeta.class;
		}

		public Issue readInfo() {
			final Issue issue = new Issue();
			issue.issueNumber = issueNumber;
			issue.meta = readMeta();
			issue.tags = readTags();
			issue.status = readStatus();
			return issue;
		}

		public ArrayList<Comment> readComments() {
			return readComments(Comment::new);
		}

		public <T extends Comment> ArrayList<T> readComments(final Supplier<T> commentFactory) {
			final File commentsRoot = getIssueCommentsDir(project, issueNumber);
			final ArrayList<T> comments = new ArrayList<>();

			if (commentsRoot.exists() && commentsRoot.isDirectory()) {
				for (final File commentDir : commentsRoot.listFiles()) {
					if (!commentDir.isDirectory()) {
						// skip unexpected files
						continue;
					}
					if (commentDir.isHidden()) {
						// skip hidden
						continue;
					}

					final T comment = commentFactory.get();
					comment.id = commentDir.getName();
					comments.add(comment);

					comment.meta = comment(commentDir).readMeta();
				}
			}
			return comments;
		}

		public CommentProxy issueComment(final String commentId) {
			return comment(project, issueNumber, commentId);
		}

		public File getAttachmentsDir() {
			return new File(getRoot(), ATTACHMENTS_DIRNAME);
		}

		public File getCommentsDir() {
			return new File(getRoot(), COMMENTS_DIRNAME);
		}

		public File getAttachmentRoot(final String attachmentId) {
			return new File(getAttachmentsDir(), attachmentId);
		}

		public File getAttachmentMetaRoot(final String attachmentId) {
			return new File(getAttachmentRoot(attachmentId), META_DIRNAME);
		}

		public ArrayList<Attachment> readAttachments() {
			final File attachmentsRoot = getAttachmentsDir();
			final ArrayList<Attachment> attachments = new ArrayList<>();

			if (attachmentsRoot.exists() && attachmentsRoot.isDirectory()) {
				for (final File attachmentDir : attachmentsRoot.listFiles()) {
					if (attachmentDir.isHidden() || !attachmentDir.isDirectory()) {
						// skip unexpected and hidden files
						continue;
					}
					attachments.add(attachment(attachmentDir).readInfo());
				}
			}
			attachments.sort(ATTACHMENTS_BY_DATE_NAME);

			return attachments;
		}

		public AttachmentProxy issueAttachment(final String id) {
			return attachment(project, issueNumber, id);
		}

		/**
		 * Creates new attachment within the current issue, copies a binary file from provided source to attachment
		 * folder, generates an appropriate metadata.json.
		 *
		 * @param uploadFile   File to copy
		 * @return DTO with fully expanded attachment info
		 * @throws IOException At file operations errors
		 */
		public Attachment uploadAttachment(final MultipartFile uploadFile) throws Exception {
			final AttachmentMeta.UploadMeta uploadMeta = new AttachmentMeta.UploadMeta();
			uploadMeta.originalFileName = uploadFile.getOriginalFilename();
			uploadMeta.name = uploadFile.getName();
			uploadMeta.contentType = uploadFile.getContentType();
			uploadMeta.size = uploadFile.getSize();
			LOGGER.debug("Upload: {}", uploadMeta);
			final Date fileNameDate = extractDateFromFilename(uploadMeta.originalFileName);
			return uploadAttachment2(uploadMeta, (fileNameDate == null) ? ZonedDateTime.now() :
					ZonedDateTime.ofInstant(fileNameDate.toInstant(), ZoneId.systemDefault()), uploadFile::transferTo);
		}

		public Attachment uploadAttachment(final String originalFileName, final String contentType, final long size,
										   final ZonedDateTime creationDate, final FileSaver saverCallback) throws Exception {
			final AttachmentMeta.UploadMeta uploadMeta = new AttachmentMeta.UploadMeta();
			uploadMeta.originalFileName = originalFileName;
			uploadMeta.name = originalFileName;
			uploadMeta.contentType = contentType;
			uploadMeta.size = size;
			LOGGER.debug("Upload: {} {}", uploadMeta, creationDate);
			return uploadAttachment2(uploadMeta, creationDate, saverCallback);
		}

		public Attachment uploadAttachment2(final AttachmentMeta.UploadMeta uploadMeta,
											final ZonedDateTime creationDate, final FileSaver saverCallback) throws Exception {
			final File attachmentsDir = getAttachmentsDir();
			checkOrCreateDirectory(attachmentsDir);

			final File attachmentFolder = createAttachmentFolder(attachmentsDir, creationDate);
			final AttachmentMeta attachmentMeta = new AttachmentMeta();
			attachmentMeta.upload = uploadMeta;
			attachmentMeta.fileName = uploadMeta.originalFileName;
			attachmentMeta.title = attachmentMeta.fileName;
			attachmentMeta.creationDate = Date.from(creationDate.toInstant());

			writeMetaMeta(attachmentFolder, attachmentMeta);
			final Attachment attachment = new Attachment();
			attachment.meta = attachmentMeta;
			attachment.id = attachmentFolder.getName();

			final File attachmentFile = new File(attachmentFolder.getAbsolutePath(), attachmentMeta.fileName);
			LOGGER.debug("Dest attachment file: " + attachmentFile.getAbsolutePath());
			if (attachmentFile.exists()) {
				LOGGER.warn("Attachment file already exists: " + attachmentFile.getAbsolutePath());
			}
			saverCallback.saveTo(attachmentFile);
			return attachment;
		}

		public void updateAttachmentPreviews() throws IOException {
			final File issueAttachmentsDir = getAttachmentsDir();

			if (!issueAttachmentsDir.exists()) {
				return;
			}

			for (final File dir : issueAttachmentsDir.listFiles()) {
				if (dir.isHidden() || !dir.isDirectory()) {
					continue;
				}

				final AttachmentProxy attachmentProxy = new AttachmentProxy(dir);
				attachmentProxy.updatePreview();
			}
		}

		public String newCommentId(final ZonedDateTime creationDate) {
			final Set<String> existingFiles = new TreeSet<>();
			final String[] commentsDirFiles = getCommentsDir().list();
			if (commentsDirFiles != null) {
				existingFiles.addAll(Arrays.asList(commentsDirFiles));
			}
			LOGGER.debug("Already existing comment ids: {}", existingFiles);
			final String shortId = shortIdGenerator.createId(creationDate, existingFiles);
			LOGGER.debug("Comment Id: {}", shortId);
			return shortId;
		}
	}

	public IssueProxy issue(final String project, final String issueNumber) {
		return new IssueProxy(project, issueNumber);
	}

	public IssueProxy issue(final File rootDir) {
		return new IssueProxy(rootDir);
	}

	public class CommentProxy extends Proxy<CommentMeta> {
		final File rootDir;
		final String issueNumber;
		final String project;

		private CommentProxy(final String project, final String issueNumber, final String commentId) {
			this.rootDir = getCommentRoot(project, issueNumber, commentId);
			this.project = project;
			this.issueNumber = issueNumber;
		}

		private CommentProxy(final File rootDir) {
			this.rootDir = rootDir;
			final File issueDir = rootDir.getParentFile().getParentFile();
			final File projectDir = issueDir.getParentFile().getParentFile();
			issueNumber = issueDir.getName();
			project = projectDir.getName();
		}

		@Override
		protected File getRoot() {
			return rootDir;
		}

		@Override
		protected Class<CommentMeta> valueType() {
			return CommentMeta.class;
		}

		public Comment read() {
			final Comment comment = new Comment();
			comment.id = rootDir.getName();
			comment.meta = readMeta();
			comment.tags = readTags();
			comment.status = readStatus();
			return comment;
		}

		public String getDescriptionEditorPath() {
			return PROJECTS_DIRNAME + '/' + project + '/' + ISSUES_DIRNAME + '/' + issueNumber + '/' + COMMENTS_DIRNAME
					+ '/' + rootDir.getName() + '/' + DESCRIPTION_MD_FILENAME;
		}

		public String getMetaEditorPath() {
			return PROJECTS_DIRNAME + '/' + project + '/' + ISSUES_DIRNAME + '/' + issueNumber + '/' + COMMENTS_DIRNAME
					+ '/' + rootDir.getName() + '/' + META_JSON_FILENAME;
		}

		public void writeFile(final String fileName, final String content) throws IOException {
			Repository.this.writeFile(getRoot(), fileName, content);
		}
	}

	public CommentProxy comment(final String project, final String issueNumber, final String commentId) {
		return new CommentProxy(project, issueNumber, commentId);
	}

	public CommentProxy comment(final File commentDir) {
		return new CommentProxy(commentDir);
	}

	public class AttachmentProxy extends Proxy<AttachmentMeta> {
		final File rootDir;
		final File metaRoot;
		final String issueNumber;
		final String project;

		private AttachmentProxy(final String project, final String issueNumber, final String attachmentId) {
			rootDir = issue(project, issueNumber).getAttachmentRoot(attachmentId);
			metaRoot = new File(getRoot(), META_DIRNAME);
			this.project = project;
			this.issueNumber = issueNumber;
		}

		private AttachmentProxy(final File rootDir) {
			this.rootDir = rootDir;
			this.metaRoot = new File(getRoot(), META_DIRNAME);
			final File issueDir = rootDir.getParentFile().getParentFile();
			final File projectDir = issueDir.getParentFile().getParentFile();
			issueNumber = issueDir.getName();
			project = projectDir.getName();
		}

		@Override
		protected File getRoot() {
			return rootDir;
		}

		@Override
		public File getMetaRoot() {
			return metaRoot;
		}

		public String getDescriptionEditorPath() {
			return PROJECTS_DIRNAME + '/' + project + '/' + ISSUES_DIRNAME + '/' + issueNumber + '/'
					+ ATTACHMENTS_DIRNAME + '/' + getAttachmentId() + '/' + META_DIRNAME + '/'
					+ DESCRIPTION_MD_FILENAME;
		}

		public String getMetaEditorPath() {
			return PROJECTS_DIRNAME + '/' + project + '/' + ISSUES_DIRNAME + '/' + issueNumber + '/'
					+ ATTACHMENTS_DIRNAME + '/' + getAttachmentId() + '/' + META_DIRNAME + '/' + META_JSON_FILENAME;
		}

		@Override
		protected Class<AttachmentMeta> valueType() {
			return AttachmentMeta.class;
		}

		private FileInfo createFileInfo(final String fullDirUri, final String name) {
			final FileInfo info = new FileInfo();
			info.fileName = name;
			info.fullFileUri = uriEncodePath(fullDirUri + MIZOINE_DIR + '/' + info.fileName);
			return info;
		}

		private ArrayList<FileInfo> getPreviewFileInfos(final String fullDirUri, final File mizoineDir,
														final String previewFileNamePrefix) {
			if (!mizoineDir.exists()) {
				return null;
			}
			final ArrayList<FileInfo> infos = new ArrayList<>();
			for (final File thumbnailFile : mizoineDir.listFiles()) {
				if (!thumbnailFile.isFile()) {
					continue;
				}
				final String name = thumbnailFile.getName();
				if (!name.startsWith(previewFileNamePrefix)) {
					continue;
				}
				infos.add(createFileInfo(fullDirUri, name));
			}
			infos.sort(ATTACHMENT_FILES_BY_INDEX);
			return infos;
		}

		private FileInfo getInfoIfExists(final String fullDirUri, final File mizoineDir,
										 final String previewFileName) {
			if (new File(mizoineDir, previewFileName).exists()) {
				return createFileInfo(fullDirUri, previewFileName);
			}
			return null;
		}

		public Attachment readInfo() {
			final Attachment attachment = new Attachment();
			attachment.id = getAttachmentId();
			attachment.meta = readMeta();
			attachment.tags = readTags();
			attachment.status = readStatus();

			final List<String> attachmentFileNames = readAttachmentFileNames(rootDir);
			final String fullDirUri = attachmentUriBase(project, issueNumber, attachment.id);

			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("File(s) found: " + attachmentFileNames);
			}

			final ArrayList<FileInfo> infos = new ArrayList<>();

			if (attachmentFileNames != null) {
				for (final String filename : attachmentFileNames) {
					final FileInfo info = new FileInfo();
					infos.add(info);
					info.fileName = filename;
					info.fullFileUri = uriEncodePath(fullDirUri + info.fileName);
				}
			}

			attachment.files = infos;

			final File mizoineDir = getLocalMizoineDir();
			attachment.thumbnails = getPreviewFileInfos(fullDirUri, mizoineDir, THUMBNAIL_PAGE_PREFIX);
			attachment.thumbnail = getInfoIfExists(fullDirUri, mizoineDir, THUMBNAIL_JPG);
			attachment.previews = getPreviewFileInfos(fullDirUri, mizoineDir, PREVIEW_PAGE_PREFIX);
			attachment.preview = getInfoIfExists(fullDirUri, mizoineDir, PREVIEW_JPG);
			return attachment;
		}

		@NotNull
		public String getAttachmentId() {
			return rootDir.getName();
		}

		private File getFirstFile() {
			for (final File file : rootDir.listFiles()) {
				if (file.isHidden() || file.isDirectory()) {
					continue;
				}

				return file;
			}
			return null;
		}

		private AttachmentPreviewGenerator getCompatibleGenerator(final File file) {
			for (final AttachmentPreviewGenerator generator : previewGenerators) {
				if (generator.isCompatibleWith(file)) {
					return generator;
				}
			}
			return null;
		}

		public void extractDescriptionAndLogErrors() {
			try {
				extractDescription();
			} catch (final IOException e) {
				LOGGER.error("Extracting description failed in: {}", this.getRoot().getAbsolutePath(), e);
			}
		}

		public void extractDescription() throws IOException {
			final File file = getFirstFile();
			if (file == null) {
				return;
			}

			final AttachmentPreviewGenerator generator = getCompatibleGenerator(file);
			if (generator == null) {
				return;
			}

			final String markdown = generator.extractMarkdown(file);
			if (markdown != null && !markdown.isBlank()) {
				writeDescription(markdown);
			}
		}

		public void updatePreviewAndLogErrors() {
			try {
				updatePreview();
			} catch (final IOException e) {
				LOGGER.error("Update preview failed in: {}", this.getRoot().getAbsolutePath(), e);
			}
		}

		public void updatePreview() throws IOException {
			checkOrCreateHiddenDirectory(getRootMizoineDir());
			final File localMizoineDir = getLocalMizoineDir();
			checkOrCreateHiddenDirectory(localMizoineDir);
			final File centralMizoineDir = getCentralMizoineDir();
			checkOrCreateDirectory(centralMizoineDir);
			final List<File> mizoineDirs = List.of(localMizoineDir, centralMizoineDir);
			for (final File mizoineDir : mizoineDirs) {
				LOGGER.debug("Updating preview files in: " + mizoineDir.getAbsolutePath());
				cleanUpPreviewDir(mizoineDir);
			}

			final File file = getFirstFile();
			if (file == null) {
				return;
			}

			final AttachmentPreviewGenerator generator = getCompatibleGenerator(file);
			if (generator == null) {
				return;
			}

			for (final File mizoineDir : mizoineDirs) {
				final String fileExtension = getFileExtension(file.getName());
				Files.copy(file.toPath(),
						new File(mizoineDir, ATTACHMENT_COPY_FILENAME_WO_EXT + "." + fileExtension).toPath());
				generator.generatePreviews(file, mizoineDir);
			}
		}

		@NotNull
		private File getLocalMizoineDir() {
			return new File(rootDir, MIZOINE_DIR);
		}

		@NotNull
		private File getCentralMizoineDir() {
			return new File(new File(new File(getRootMizoineDir(), project), issueNumber), getAttachmentId());
		}

		private void cleanUpPreviewDir(final File mizoineDir) {
			for (final File file : mizoineDir.listFiles()) {
				final String name = file.getName();
				if (AttachmentPreviewGenerator.PREVIEW_PNG.equals(name)
						|| AttachmentPreviewGenerator.THUMBNAIL_PNG.equals(name)
						|| AttachmentPreviewGenerator.PREVIEW_JPG.equals(name)
						|| AttachmentPreviewGenerator.THUMBNAIL_JPG.equals(name)
						|| name.startsWith(AttachmentPreviewGenerator.PREVIEW_PAGE_PREFIX)
						|| name.startsWith(AttachmentPreviewGenerator.THUMBNAIL_PAGE_PREFIX)
						|| name.startsWith(ATTACHMENT_COPY_FILENAME_WO_EXT)) {
					LOGGER.info("Removing previously created preview: " + file.getAbsolutePath());
					file.delete();
				}
			}
		}

	}

	public AttachmentProxy attachment(final String project, final String issueNumber, final String attachmentId) {
		return new AttachmentProxy(project, issueNumber, attachmentId);
	}

	public AttachmentProxy attachment(final File attachmentDir) {
		return new AttachmentProxy(attachmentDir);
	}

}
