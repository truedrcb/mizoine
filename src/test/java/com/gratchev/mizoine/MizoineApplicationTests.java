package com.gratchev.mizoine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = MizoineApplication.class, properties = "mizoine.users-file=src/test/resources/users-test.yaml")
class MizoineApplicationTests {

	@Test
	void contextLoads() {
	}
}
