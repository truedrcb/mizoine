package com.gratchev.mizoine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.gratchev.mizoine.repository.Repositories;
import com.gratchev.mizoine.repository.Repository;



@SpringBootApplication
@EnableConfigurationProperties
public class Application implements WebMvcConfigurer {
	private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

	@Autowired
	private Repositories repos;

	/**
	 * See http://www.baeldung.com/spring-mvc-static-resources
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		for (final Repository repo : repos.getAllRepositories()) {
			
			final String uriBase = repo.getResourceUriBase() + "**";
			final String targetLocation = "file:///" + repo.getRoot().getAbsolutePath() + "/";
			LOGGER.info("Registering URI base: " + uriBase);
			LOGGER.info("Pointing to: " + targetLocation);
			registry
				.addResourceHandler(uriBase)
				.addResourceLocations(targetLocation);
			
		}
	}

	public static void main(String[] args) throws Exception {
		LOGGER.info("Running Mizoine");
		SpringApplication.run(Application.class, args);
	}
	
}
