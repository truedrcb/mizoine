package com.gratchev.mizoine.repository;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableAutoConfiguration
@EnableConfigurationProperties
@WebListener
public class Repositories {
	private static final String DEFAULT_REPO_KEY = "";

	private static final Logger LOGGER = LoggerFactory.getLogger(Repositories.class);

	@Value("${repository.home:sample/test_repo1}")
	protected String defaultRootPath;
	
	private Repository defaultRepo;
	
	private final Map<String, Repository> repositories = new HashMap<>();
	
	@Autowired
	protected RepositoriesConfiguration repositoriesConfiguration;

	@Bean
	@ConfigurationProperties
	public RepositoriesConfiguration repositoriesConfigurationBean() {
		return new RepositoriesConfiguration();
	}
	
	public static class Config {
		@Override
		public String toString() {
			return "Config [home=" + home + ", host=" + host + "]";
		}
		private String home;
		private String host;
		
		public String getHome() {
			return home;
		}
		public void setHome(String home) {
			this.home = home;
		}
		public String getHost() {
			return host;
		}
		public void setHost(String host) {
			this.host = host;
		}
	}
	
	public static class RepositoriesConfiguration {

		private final Map<String, Config> repositories = new HashMap<>();

		public Map<String, Config> getRepositories() {
			return this.repositories;
		}
	}
	
	public Repository getRepository(final HttpServletRequest request) {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Request headers of: " + request);
			
			final Enumeration<String> headerNames = request.getHeaderNames();
			while (headerNames.hasMoreElements()) {
				final String n = headerNames.nextElement();
				LOGGER.trace(n + ": " + request.getHeader(n) );
			}
		}
		
		final String proxyHost = request.getHeader("x-forwarded-host");
		if (proxyHost != null) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Repo by reverse proxy host: " + proxyHost);
			}
			return getRepositoryByHost(proxyHost);
		}
		
		final String host = request.getHeader("host");
		if (host != null) {
			if (LOGGER.isDebugEnabled()) {
				LOGGER.debug("Repo by host: " + host);
			}
			return getRepositoryByHost(host);
		}
		
		return getRepositoryByUrl(request.getRequestURL());
	}

	public Repository getRepositoryByHost(final String host) {
		final String key = host.toLowerCase();
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Repo by host: " + host);
		}
		return getRepository(key);
	}
	
	public Repository getRepositoryByUrl(final StringBuffer requestUrl) {
		final String key = requestUrl.toString().toLowerCase();
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("Repo by request URL: " + requestUrl);
		}
		return getRepository(key);
	}
	
	private Repository initializeRepositoryIfConfigured(final String host) {
		if (repositoriesConfiguration != null) {
			final Map<String, Config> configurations = repositoriesConfiguration.getRepositories();
			if (configurations != null) {
				
				
				for (final Entry<String, Config> e : configurations.entrySet()) {
					final Config config = e.getValue();
					if (host.startsWith(config.getHost())) {
						final Repository repo = new Repository(config.getHome(), e.getKey());
						repositories.put(config.getHost(), repo);
						return repo;
					}
				}
			}
		}
		return null;
	}

	private Repository getRepository(final String host) {
		
		Repository repo = repositories.get(host);
		
		if (repo != null) {
			return repo;
		}
		
		for (final Entry<String, Repository> e : repositories.entrySet()) {
			final String key = e.getKey();
			
			if (host.startsWith(key)) {
				return e.getValue();
			}
		}
		
		repo = initializeRepositoryIfConfigured(host);
		
		if (repo != null) {
			return repo;
		}
		
		if (defaultRepo == null) {
			defaultRepo = new Repository(defaultRootPath, DEFAULT_REPO_KEY);
		}
		
		return defaultRepo;
	}
	

	public Iterable<Repository> getAllRepositories() {
		final Map<String, Repository> repos = new HashMap<>();
		
		repos.put(DEFAULT_REPO_KEY, getRepository(DEFAULT_REPO_KEY));
		for (final Entry<String, Config> e : repositoriesConfiguration.repositories.entrySet()) {
			LOGGER.info("Found repo: " + e.getKey() + " - " + e.getValue());
			repos.put(e.getKey(), getRepository(e.getValue().getHost()));
		}
		
		return repos.values();
	}

}
