package com.gratchev.mizoine.repository;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gratchev.mizoine.repository.Repositories.RepositoriesConfiguration;
import com.gratchev.mizoine.repository.Repositories.Config;

public class RepositoriesTestConfig {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(RepositoriesTestConfig.class);
	
	@Test
	public void testDefaultRepo() {
		final Repositories reps = new Repositories();
		reps.defaultRootPath = "sample/test1";
		
		final Repository r1 = reps.getRepositoryByUrl(new StringBuffer());
		
		assertNotNull(r1);
		
		assertEquals(new File("sample/test1"), r1.getRoot());
		
		assertEquals("/attachments/", r1.getResourceUriBase());
	}
	
	@Test
	public void testMultipleReposByUrl() {
		final Repositories reps = new Repositories();
		reps.defaultRootPath = "sample/test1";
		reps.repositoriesConfiguration = new RepositoriesConfiguration() {
			{
				getRepositories().put("test1", new Config() {
					{
						setHome("/tmp/1");
						setHost("http://test.com");
					}
				});
				getRepositories().put("2nd", new Config() {
					{
						setHome("/dev/null");
						setHost("http://localhost:8080");
					}
				});
			}
		};
		
		final Repository r1 = reps.getRepositoryByUrl(new StringBuffer("https://127.0.0.1:4443"));
		
		assertNotNull(r1);		
		assertEquals(new File("sample/test1"), r1.getRoot());
		assertEquals("/attachments/", r1.getResourceUriBase());

		final Repository r2 = reps.getRepositoryByUrl(new StringBuffer("http://localhost:8080"));
		
		assertNotNull(r2);		
		assertEquals(new File("/dev/null"), r2.getRoot());
		assertEquals("/attachments/2nd/", r2.getResourceUriBase());
		
		final Repository r3 = reps.getRepositoryByUrl(new StringBuffer("http://test.com/projects/list"));
		
		assertNotNull(r3);		
		assertEquals(new File("/tmp/1"), r3.getRoot());
		assertEquals("/attachments/test1/", r3.getResourceUriBase());
	}

	@Test
	public void testMultipleReposByHost() {
		final Repositories reps = new Repositories();
		reps.defaultRootPath = "sample/test1";
		reps.repositoriesConfiguration = new RepositoriesConfiguration() {
			{
				getRepositories().put("test1", new Config() {
					{
						setHome("/tmp/1");
						setHost("test.com");
					}
				});
				getRepositories().put("2nd", new Config() {
					{
						setHome("/dev/null");
						setHost("localhost:8080");
					}
				});
			}
		};
		
		final Repository r1 = reps.getRepositoryByHost("127.0.0.1:4443");
		
		assertNotNull(r1);		
		assertEquals(new File("sample/test1"), r1.getRoot());
		assertEquals("/attachments/", r1.getResourceUriBase());

		final Repository r2 = reps.getRepositoryByHost("localhost:8080");
		
		assertNotNull(r2);		
		assertEquals(new File("/dev/null"), r2.getRoot());
		assertEquals("/attachments/2nd/", r2.getResourceUriBase());
		
		final Repository r3 = reps.getRepositoryByHost("test.com:80");
		
		assertNotNull(r3);		
		assertEquals(new File("/tmp/1"), r3.getRoot());
		assertEquals("/attachments/test1/", r3.getResourceUriBase());
	}
}
