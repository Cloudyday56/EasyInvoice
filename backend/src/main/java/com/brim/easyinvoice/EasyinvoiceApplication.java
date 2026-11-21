package com.brim.easyinvoice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // runs PipelineRunner.tick() on a timer
public class EasyinvoiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EasyinvoiceApplication.class, args);
	}

}
