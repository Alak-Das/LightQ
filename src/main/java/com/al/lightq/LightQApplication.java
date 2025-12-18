package com.al.lightq;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * Main entry point for the LightQ application.
 * <p>
 * This class initializes the Spring Boot application and enables caching.
 * It also defines the OpenAPI documentation for the LightQ API.
 * </p>
 */
@SpringBootApplication
@EnableCaching
@OpenAPIDefinition(info = @Info(title = "LightQ API", version = "1.0", description = "API for LightQ - A lightweight message queue service"))
public class LightQApplication {

	/**
	 * Main method to run the Spring Boot application.
	 *
	 * @param args command line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.run(LightQApplication.class, args);
	}

}
