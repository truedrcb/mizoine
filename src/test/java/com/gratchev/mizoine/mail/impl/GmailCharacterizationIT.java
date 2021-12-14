package com.gratchev.mizoine.mail.impl;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

public class GmailCharacterizationIT {
	private static final Logger log = LoggerFactory.getLogger(GmailCharacterizationIT.class);

	/**
	 * <a
	 * href=https://developers.google.com/identity/protocols/oauth2/web-server#offline7>Refreshing
	 * an access token (offline access)</a>
	 */
	@Test
	@Disabled
	void refreshingAnAccessTokenOfflineAccess() {
		RestTemplate restTemplate = new RestTemplate();
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
		map.add("id", "1");

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
		log.info("Response: {}",
				restTemplate.postForEntity("https://oauth2.googleapis.com/token", request, String.class));
	}
}
