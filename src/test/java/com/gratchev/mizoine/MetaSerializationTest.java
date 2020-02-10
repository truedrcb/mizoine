package com.gratchev.mizoine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Sets;
import com.gratchev.mizoine.repository.meta.AttachmentMeta;
import com.gratchev.mizoine.repository.meta.BaseMeta;
import com.gratchev.mizoine.repository.meta.CommentMeta;
import com.gratchev.mizoine.repository.meta.IssueMeta;
import com.gratchev.mizoine.repository.meta.RepositoryMeta;
import com.gratchev.mizoine.repository.meta.RepositoryMeta.TagMeta;;

public class MetaSerializationTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(MetaSerializationTest.class);

	final ObjectMapper mapper = new ObjectMapper();
	
	@BeforeEach
	public void setup() {
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
	}

	@Test
	public void testSimpleMeta() throws JsonProcessingException {
		final BaseMeta meta1 = new IssueMeta();
		meta1.title = "Base Issue Meta";
		meta1.creationDate = new Date();
		final String valueAsString = mapper.writeValueAsString(meta1);
		LOGGER.info(valueAsString);
		assertNotNull(valueAsString);
	}

	@Test
	public void testAttachmentMeta() throws JsonProcessingException {
		final AttachmentMeta meta1 = new AttachmentMeta();
		meta1.title = "An attachment 1";
		meta1.creationDate = new Date();

		final String valueAsString = mapper.writeValueAsString(meta1);
		LOGGER.info(valueAsString);
		assertNotNull(valueAsString);
	}

	@Test
	public void testRepositoryMeta() throws JsonProcessingException {
		final RepositoryMeta meta1 = new RepositoryMeta();
		meta1.title = "A test repository with all parameters";
		meta1.creationDate = new Date();
		meta1.tags = new HashMap<>();
		
		{
			final TagMeta tag = new TagMeta();
			tag.badgeStyle = "info";
			tag.icon = "fas fa-tag";
			tag.synonyms = Sets.newHashSet("alias", "badge");
			meta1.tags.put("tag1", tag);
		}

		{
			final TagMeta tag = new TagMeta();
			tag.badgeStyle = "danger";
			tag.icon = "fab fa-mizoine";
			meta1.tags.put("tag Tag", tag);
			
		}
		

		final String valueAsString = mapper.writeValueAsString(meta1);
		LOGGER.info(valueAsString);
		assertNotNull(valueAsString);
	}
	
	@Test
	public void testIssueMeta() throws IOException {
		final IssueMeta meta1 = new IssueMeta();
		meta1.title = "To do: check how JSON serealizes maps";
		meta1.creationDate = new Date();

		final String valueAsString = mapper.writeValueAsString(meta1);
		LOGGER.info(valueAsString);
		assertNotNull(valueAsString);
	}
	
	@Test
	public void readIssue1Meta() throws IOException {
		final IssueMeta i1 = mapper.readValue(MetaSerializationTest.class.getResourceAsStream("issue1.json"), IssueMeta.class);
		assertNotNull(i1);
		LOGGER.info(i1.toString());
	}

	@Test
	public void readIssue2Meta() throws IOException {
		final IssueMeta i1 = mapper.readValue(MetaSerializationTest.class.getResourceAsStream("issue2-short-date.json"), IssueMeta.class);
		assertNotNull(i1);
		LOGGER.info(i1.toString());
	}

	@Test
	public void readIssue3Meta() throws IOException {
		final IssueMeta i1 = mapper.readValue(MetaSerializationTest.class.getResourceAsStream("issue3-short-date.json"), IssueMeta.class);
		assertNotNull(i1);
		LOGGER.info(i1.toString());
	}

	@Test
	public void readIssue4Meta() throws IOException {
		final IssueMeta i1 = mapper.readValue(MetaSerializationTest.class.getResourceAsStream("issue4-short-date.json"), IssueMeta.class);
		assertNotNull(i1);
		LOGGER.info(i1.toString());
	}

	@Test
	public void readIssueEmptyMeta() {
		assertThrows(JsonProcessingException.class, 
				() -> {
				mapper.readValue(MetaSerializationTest.class.getResourceAsStream("issue-meta-empty.json"), IssueMeta.class);
			}
		);
	}

	@Test
	public void testCommentMeta() throws IOException {
		final CommentMeta meta1 = new CommentMeta();
		meta1.title = "Non-Mail comment";
		meta1.creationDate = new Date();

		final String valueAsString = mapper.writeValueAsString(meta1);
		LOGGER.info(valueAsString);
		assertNotNull(valueAsString);
	}

	@Test
	public void testMailCommentMeta() throws IOException {
		final CommentMeta meta1 = new CommentMeta();
		meta1.title = "Re: Mail comment";
		meta1.creationDate = new Date();
		meta1.messageId = "ABC209aeEsVEST";
		meta1.messageHeaders = new TreeMap<>();
		
		meta1.messageHeaders.put("from", "Mizoine <mizoine@my.mizoine>");
		meta1.messageHeaders.put("to", "Artem <artem@gratchev.com>");
		meta1.messageHeaders.put("cc", "User1 <1@test.test>; User2 <u2@test.test>");

		final String valueAsString = mapper.writeValueAsString(meta1);
		LOGGER.info(valueAsString);
		assertNotNull(valueAsString);
	}
}
