package com.gratchev.mizoine.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gratchev.mizoine.GitComponent;
import com.gratchev.mizoine.GitComponent.GitFileInfo;

public class RepositoryTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryTest.class);

	
	Repository repo;

	@Before
	public void setUp() throws Exception {
		repo = new Repository("sample/test_repo1", "");
		
		assertTrue("Directory must exist to continue: " + repo.getRoot().getAbsolutePath(), repo.getRoot().exists());
	}

	@Test
	public void testVerifyRepositoryAndGenerateLog() throws IOException {
		repo.verifyRepositoryAndGenerateLog();
	}

	@Test
	public void jsonParser() throws JsonParseException, IOException {
		// https://github.com/FasterXML/jackson-core/wiki/JsonParser-Features
		
		final JsonFactory f = new JsonFactory();
		f.enable(JsonParser.Feature.ALLOW_COMMENTS);
		final JsonParser p = f.createParser(RepositoryTest.class.getResourceAsStream("custom1.json"));
		p.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
		
		p.close();
	}
	
	
	@Test
	public void treeParser() throws JsonParseException, IOException {
		// http://www.studytrails.com/java/json/java-jackson-json-tree-parsing/

		final JsonFactory f = new JsonFactory();
		f.enable(JsonParser.Feature.ALLOW_COMMENTS);
		
		// create an ObjectMapper instance.
		final ObjectMapper mapper = new ObjectMapper(f);
		// use the ObjectMapper to read the json string and create a tree
		final JsonNode node = mapper.readTree(RepositoryTest.class.getResourceAsStream("custom1.json"));

		// lets see what type the node is
		LOGGER.info("" + node.getNodeType()); // prints OBJECT
		// is it a container
		LOGGER.info("" + node.isContainerNode()); // prints true

		LOGGER.info("" + node.get("title").asText());
		LOGGER.info("" + node.get("testAttributeUnmapped1").asText());
	}

	@Test
	public void identifyFileProject() throws IOException {
		final GitComponent gitC = new GitComponent(repo);
		final String repoRootPrefix = gitC.getRepoRootPrefix();
		LOGGER.info(repoRootPrefix);
		final String path1 = "sample/test_repo1/projects/HOME/tags/private";
		
		assertTrue(path1.startsWith(repoRootPrefix));
		
		final GitFileInfo info = gitC.identifyFile(path1, new GitFileInfo());
		
		LOGGER.info(info.toString());
		
		assertEquals("HOME", info.project);
		assertNull(info.issueNumber);
		assertNull(info.commentId);
		assertNull(info.attachmentId);
	}

	@Test
	public void identifyFileTitle() throws IOException {
		final GitComponent gitC = new GitComponent(repo);

		final GitFileInfo info = new GitFileInfo();
				
		gitC.identifyFile("sample/test_repo1/projects/DEV/description.md", info);
		LOGGER.info(info.toString());
		assertEquals("DEV", info.project);
		assertNull(info.issueNumber);
		assertNull(info.commentId);
		assertNull(info.attachmentId);
		//assertEquals("Development", info.title);

		gitC.identifyFile("sample/test_repo1/projects/DEV/issues", info);
		LOGGER.info(info.toString());
		assertEquals("DEV", info.project);
		assertNull(info.issueNumber);
		assertNull(info.commentId);
		assertNull(info.attachmentId);
		//assertEquals("Development", info.title);

		gitC.identifyFile("sample/test_repo1/projects/DEV/issues/500", info);
		LOGGER.info(info.toString());
		assertEquals("DEV", info.project);
		assertEquals("500", info.issueNumber);
		assertNull(info.commentId);
		assertNull(info.attachmentId);
		//assertNull(info.title);

		gitC.identifyFile("sample/test_repo1/projects/HOME/issues/3", info);
		LOGGER.info(info.toString());
		assertEquals("HOME", info.project);
		assertEquals("3", info.issueNumber);
		assertNull(info.commentId);
		assertNull(info.attachmentId);
		//assertEquals("Issue without description, comments and attachments", info.title);

		gitC.identifyFile("sample/test_repo1/projects/DEV/issues/0/attachments/hQzzR1/file bootstrap-stack.png", info);
		LOGGER.info(info.toString());
		assertEquals("DEV", info.project);
		assertEquals("0", info.issueNumber);
		assertNull(info.commentId);
		assertEquals("hQzzR1", info.attachmentId);
		//assertEquals("A PNG file", info.title);

		gitC.identifyFile("sample/test_repo1/projects/DEV/issues/0/attachments/SphSp0/file icon-spring-boot.svg", info);
		LOGGER.info(info.toString());
		assertEquals("DEV", info.project);
		assertEquals("0", info.issueNumber);
		assertNull(info.commentId);
		assertEquals("SphSp0", info.attachmentId);
		//assertEquals("file icon-spring-boot.svg", info.title);
	}

}
