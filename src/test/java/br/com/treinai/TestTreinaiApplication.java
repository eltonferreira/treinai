package br.com.treinai;

import org.springframework.boot.SpringApplication;

public class TestTreinaiApplication {

	public static void main(String[] args) {
		SpringApplication.from(TreinaiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
