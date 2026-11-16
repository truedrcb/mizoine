package com.gratchev.mizoine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.gratchev.mizoine.users.HashPasswordCommand;

@SpringBootApplication
public class MizoineApplication {

	public static void main(final String[] args) {
		if (HashPasswordCommand.isRequested(args)) {
			System.exit(HashPasswordCommand.fromSystem().run());
		}
		SpringApplication.run(MizoineApplication.class, args);
	}
}
