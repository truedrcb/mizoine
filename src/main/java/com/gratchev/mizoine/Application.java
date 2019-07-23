package com.gratchev.mizoine;

import com.gratchev.mizoine.repository.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@EnableConfigurationProperties
public class Application implements WebMvcConfigurer {
	private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

	@Autowired
	private CommonController commonController;

	/**
	 * See http://www.baeldung.com/spring-mvc-static-resources
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		final Repository repo = commonController.getRepo();

		final String uriBase = repo.getResourceUriBase() + "**";
		final String targetLocation = "file:///" + repo.getRoot().getAbsolutePath() + "/";
		LOGGER.info("Registering URI base: " + uriBase);
		LOGGER.info("Pointing to: " + targetLocation);
		registry
			.addResourceHandler(uriBase)
			.addResourceLocations(targetLocation);
	}

	public static void main(String[] args) throws Exception {
		LOGGER.info("Running Mizoine");
		SpringApplication.run(Application.class, args);
	}

	@Bean
	@ConfigurationProperties
	public BasicUsersConfiguration basicUserConfigurationBean() {
		return new BasicUsersConfiguration();
	}

	public static class BasicUsersConfiguration {

		private Map<String, UserCredentials>
				users = new HashMap<String, UserCredentials>();

		public Map<String, UserCredentials> getUsers() {
			return this.users;
		}
	}

	public static class LoginCredentials {
		private String username;
		private String password;

		public String getUsername() {
			return username;
		}
		public void setUsername(String username) {
			this.username = username;
		}
		public String getPassword() {
			return password;
		}
		public void setPassword(String password) {
			this.password = password;
		}
	}

	public static class ImapConnection extends LoginCredentials {
		private String host;
		private int port;

		public String getHost() {
			return host;
		}
		public void setHost(String host) {
			this.host = host;
		}
		public int getPort() {
			return port;
		}
		public void setPort(int port) {
			this.port = port;
		}
	}

	public static class UserCredentials {
		private String password;
		private LoginCredentials git;
		private ImapConnection imap;
		private String eMail;

		public LoginCredentials getGit() {
			return git;
		}

		public void setGit(LoginCredentials git) {
			this.git = git;
		}

		public ImapConnection getImap() {
			return imap;
		}

		public void setImap(ImapConnection imap) {
			this.imap = imap;
		}

		public String geteMail() {
			return eMail;
		}

		public void seteMail(String eMail) {
			this.eMail = eMail;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}
	}
}
