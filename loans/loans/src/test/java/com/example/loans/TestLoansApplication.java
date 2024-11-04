package com.example.loans;

import org.springframework.boot.SpringApplication;

public class TestLoansApplication {

	public static void main(String[] args) {
		SpringApplication.from(LoansApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
