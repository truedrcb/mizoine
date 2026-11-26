package com.gratchev.mizoine.uploads;

import java.time.Clock;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.gratchev.mizoine.MizoineProperties;
import com.gratchev.mizoine.repositories.RepositoryFiles;

/** Steps 75–77: upload links (D82–D89). The time is the system's; a test may give its own {@link Clock}. */
@Configuration
class UploadsConfig {

	@Bean
	UploadLinks uploadLinks(final ObjectProvider<Clock> clock) {
		return new UploadLinks(clock.getIfAvailable(Clock::systemUTC));
	}

	@Bean
	UploadLinkService uploadLinkService(final RepositoryFiles files, final UploadLinks links,
			final MizoineProperties properties) {
		return new UploadLinkService(files, links, Optional.ofNullable(properties.publicUrl()),
				properties.maxFileSize().toBytes());
	}
}
