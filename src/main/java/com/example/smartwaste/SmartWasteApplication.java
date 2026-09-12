package com.example.smartwaste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartWasteApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartWasteApplication.class, args);
	}

}
