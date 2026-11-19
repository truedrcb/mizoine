package com.gratchev.mizoine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(classes = MizoineApplication.class)
class MizoineApplicationTests {

	private final Environment environment;

	MizoineApplicationTests(@Autowired final Environment environment) {
		this.environment = environment;
	}

	@Test
	void contextLoads() {
	}

	@Test
	void defaultPortIsMizoineInDigits() {
		// M-I-Z-O-I-N-E: M and N skipped, I=1, Z=2, O=0, I=1, E=3
		assertThat(environment.getProperty("server.port")).isEqualTo("12013");
	}
}
