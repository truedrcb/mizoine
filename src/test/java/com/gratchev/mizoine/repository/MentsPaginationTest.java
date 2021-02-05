package com.gratchev.mizoine.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gratchev.mizoine.ShortIdGenerator;
import com.gratchev.mizoine.SignedInUserComponentMock;
import com.gratchev.mizoine.repository.Repository.CommentProxy;
import com.gratchev.mizoine.repository.TempRepositoryUtils.TempRepository;

public class MentsPaginationTest {
	TempRepository repo;
	
	@BeforeEach
	void setup() throws IOException {
		repo = TempRepository.create();
	}

	@AfterEach
	void tearDown() throws IOException {
		repo.dispose();
	}
	
	@Test
	void testReadComments() throws IOException {
		final ZonedDateTime now = ZonedDateTime.now();
		for (int i = 0; i < 100; i++) {
			final ZonedDateTime date = now.minusHours(i);
			final String title = "Comment " + i;
			final CommentProxy comment = repo.comment("PRO", "1", ShortIdGenerator.mizCodeFor(date));
			comment.createDirs();
			comment.updateMeta(meta -> {
				meta.creationDate = Date.from(date.toInstant());
				meta.title = title;
			}, new SignedInUserComponentMock());
		}
		
		assertThat(repo.issue("PRO", "1").readComments()).hasSize(100);
	}

}
