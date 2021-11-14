package com.gratchev.mizoine.repository2.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gratchev.mizoine.repository2.Configuration;

public class ConfigurationImpl implements Configuration {

	public static class ConfigurationDto {
		public Map<String, Map> users;
		public Map<String, RepositoryDto> repositories;
	}
	
	private final ConfigurationDto configuration;

	public ConfigurationImpl(final InputStream resourceAsStream) throws IOException {
		configuration = new ObjectMapper().readValue(resourceAsStream, ConfigurationDto.class);
		configuration.repositories.forEach((k, v) -> v.id = k);
	}

	@Override
	public Map<String, RepositoryDto> getRepositories() {
		return configuration.repositories;
	}

}
