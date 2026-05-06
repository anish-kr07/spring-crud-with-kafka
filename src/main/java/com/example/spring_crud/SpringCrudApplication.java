package com.example.spring_crud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling activates @Scheduled methods (used by OutboxRelay).
// Without this annotation, @Scheduled is silently ignored.
@SpringBootApplication
@EnableScheduling
public class SpringCrudApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringCrudApplication.class, args);
		System.out.println("Tomcat started on port 8080");
	}

}
