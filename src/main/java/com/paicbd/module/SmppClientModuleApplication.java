package com.paicbd.module;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author <a href="mailto:enmanuelcalero61@gmail.com"> Enmanuel Calero </a>
 * @author <a href="mailto:ndiazobed@gmail.com"> Obed Navarrete </a>
 */
@SpringBootApplication
@EnableScheduling
public class SmppClientModuleApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmppClientModuleApplication.class, args);
	}
}
