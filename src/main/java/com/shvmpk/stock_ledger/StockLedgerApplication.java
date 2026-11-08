package com.shvmpk.stock_ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StockLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockLedgerApplication.class, args);
	}

}
