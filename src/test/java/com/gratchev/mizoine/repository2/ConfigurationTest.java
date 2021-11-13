package com.gratchev.mizoine.repository2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.gratchev.mizoine.repository2.file.ConfigurationImpl;

public class ConfigurationTest {
	@Test
	void readSample() throws IOException{
		final Configuration cut = new ConfigurationImpl(ConfigurationTest.class.getResourceAsStream("mizoine-config-sample1.json"));
		assertThat(cut.getRepositories()).hasSize(2).containsKeys("home", "work");
	}
}
