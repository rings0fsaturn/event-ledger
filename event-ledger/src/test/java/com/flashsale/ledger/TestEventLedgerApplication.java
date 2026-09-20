package com.flashsale.ledger;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;

public class TestEventLedgerApplication {

	static {
	    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}
	
	public static void main(String[] args) {
		SpringApplication.from(EventLedgerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
