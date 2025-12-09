package com.al.lightq;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@OpenAPIDefinition(info = @Info(title = "LightQ API", version = "1.0", description = "API for LightQ - A lightweight message queue service"))
public class LightQApplication {

	public static void main(String[] args) {
		SpringApplication.run(LightQApplication.class, args);
	}

}
