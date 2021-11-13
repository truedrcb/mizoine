package com.gratchev.mizoine.repository2;

import java.util.Map;

public interface Configuration {

	public static class RepositoryDto {
		public String name;
		public String home;
	}

	Map<String, RepositoryDto> getRepositories();

}
