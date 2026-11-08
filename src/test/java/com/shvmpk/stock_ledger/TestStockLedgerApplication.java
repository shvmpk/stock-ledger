package com.shvmpk.stock_ledger;

import org.springframework.boot.SpringApplication;

public class TestStockLedgerApplication {

	public static void main(String[] args) {
		SpringApplication.from(StockLedgerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
