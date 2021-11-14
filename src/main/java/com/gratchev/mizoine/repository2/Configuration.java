package com.gratchev.mizoine.repository2;

import java.util.Map;

public interface Configuration {

	class RepositoryDto {
		public String id;
		public String home;
		
		public RepositoryDto() {
		}

		public RepositoryDto(final String home) {
			this.home = home;
		}
	}

	Map<String, RepositoryDto> getRepositories();

}
