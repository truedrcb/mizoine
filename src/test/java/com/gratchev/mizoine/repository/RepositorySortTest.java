package com.gratchev.mizoine.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.gratchev.mizoine.repository.Attachment.FileInfo;

public class RepositorySortTest {
	@Test
	void sortFileInfosById() {
		final List<FileInfo> infos = List
				.of("thumbnail-1", "thumbnail-10", "thumbnail-100", "thumbnail-2", "thumbnail-6").stream().map(name -> {
					final FileInfo info = new FileInfo();
					info.fileName = name + ".jpg";
					return info;
				}).collect(Collectors.toList());
		infos.sort(Repository.ATTACHMENT_FILES_BY_INDEX);
		assertThat(infos).extracting(i -> {
			return i.fileName;
		}).containsExactly("thumbnail-1.jpg", "thumbnail-2.jpg", "thumbnail-6.jpg", "thumbnail-10.jpg", "thumbnail-100.jpg");
	}
	
	@Test
	void compareTest() {
		compare("thumbnail-1", "thumbnail-2");
		compare("thumbnail-2", "thumbnail-3");
		compare("thumbnail-2", "thumbnail-10");
		compare("thumbnail-1", "thumbnail-6");
		compare("thumbnail-1", "thumbnail-9");
		compare("thumbnail-1", "thumbnail-10");
		compare("thumbnail-1", "thumbnail-20");
		compare("thumbnail-10", "thumbnail-20");
		compare("thumbnail-40", "thumbnail-300");
	}

	private void compare(final String name1, final String name2) {
		final FileInfo info1 = new FileInfo();
		info1.fileName = name1;
		final FileInfo info2 = new FileInfo();
		info2.fileName = name2;
		assertThat(Repository.ATTACHMENT_FILES_BY_INDEX.compare(info2, info1)).as("'%s' must be before '%s'", name1, name2).isGreaterThan(0);
		info1.fileName = name1 + ".jpg";
		info2.fileName = name2 + ".jpg";
		assertThat(Repository.ATTACHMENT_FILES_BY_INDEX.compare(info2, info1)).as("'%s.jpg' must be before '%s.jpg'", name1, name2).isGreaterThan(0);
	}
}
