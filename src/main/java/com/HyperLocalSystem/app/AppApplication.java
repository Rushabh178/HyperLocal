package com.HyperLocalSystem.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class AppApplication {

	public static void main(String[] args) {
		// Windows JVMs report the legacy zone "Asia/Calcutta", which newer PostgreSQL versions reject
		// when the JDBC driver connects. Same zone, current name.
		if ("Asia/Calcutta".equals(TimeZone.getDefault().getID())) {
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
		}
		SpringApplication.run(AppApplication.class, args);
	}

}
