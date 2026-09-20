package com.premchemicals.cleaningbackend;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.util.TimeZone;

@SpringBootApplication
public class CleaningbackendApplication {

	@PostConstruct
	public void init() {
		// Set JVM default timezone to Indian Standard Time (IST)
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
	}

	public static void main(String[] args) {
		SpringApplication.run(CleaningbackendApplication.class, args);
	}

}
