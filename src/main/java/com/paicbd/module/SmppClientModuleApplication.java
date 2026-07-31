package com.paicbd.module;

import com.paicbd.smsc.utils.Generated;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author <a href="mailto:enmanuelcalero61@gmail.com"> Enmanuel Calero </a>
 * @author <a href="mailto:ndiazobed@gmail.com"> Obed Navarrete </a>
 */
@Generated
@EnableScheduling
@SpringBootApplication
public class SmppClientModuleApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmppClientModuleApplication.class, args);
	}
}
