package com.gratchev.mizoine.repository;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.gratchev.mizoine.SignedInUserComponentMock;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;

public class RepositoryTestTags {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryUtilsTest.class);

	TempRepository repo;
	

	@Before
	public void setUp() throws Exception {
		repo = TempRepository.create();
		
	}
	
	@After
	public void tearDown() throws IOException {
		repo.dispose();
	}
	
	@Test
	public void addTags() throws IOException {
		final Issue i1 = repo.createIssue(repo.getProjectRoot("TEST"), 
				"Tagged issue 1", "Lorem ipsum dolor sit amet", new SignedInUserComponentMock());
		
		assertNotNull(i1.issueNumber);
		
		{
			final Issue info = repo.issue("TEST", i1.issueNumber).readInfo();
			
			assertNull(info.tags);
			assertNull(info.status);
		}
		
		repo.issue("TEST", i1.issueNumber).addTags("open");

		{
			final Issue info = repo.issue("TEST", i1.issueNumber).readInfo();

			assertNotNull(info.tags);
			assertEquals(Sets.newHashSet("open"), info.tags);
		}

		repo.issue("TEST", i1.issueNumber).addTags("open", "new", "test");

		{
			final Issue info = repo.issue("TEST", i1.issueNumber).readInfo();

			assertNotNull(info.tags);
			assertEquals(Sets.newHashSet("open", "new", "test"), info.tags);
		}

	}
	
	@Test
	public void removeTags() throws IOException {
		final String project = "TEST";
		final String issueNumber = "14";
		
		repo.issue(project, issueNumber).addTags("open2", "new-tag", "test space");

		{
			final Issue info = repo.issue(project, issueNumber).readInfo();

			assertNotNull(info.tags);
			assertEquals(Sets.newHashSet("open2", "new-tag", "test space"), info.tags);
		}

		repo.issue(project, issueNumber).removeTags("open2", "nonexisting", "12345 78");

		{
			final Issue info = repo.issue(project, issueNumber).readInfo();

			assertNotNull(info.tags);
			assertEquals(Sets.newHashSet("new-tag", "test space"), info.tags);
		}

		repo.issue(project, issueNumber).removeTags("open2", "new-tag", "test space");

		{
			final Issue info = repo.issue(project, issueNumber).readInfo();

			assertNotNull(info.tags);
			assertEquals(Sets.newHashSet(), info.tags);
		}
	}

}
