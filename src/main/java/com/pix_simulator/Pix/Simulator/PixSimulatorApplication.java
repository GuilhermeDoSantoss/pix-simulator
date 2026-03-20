package com.pix_simulator.Pix.Simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação PIX Simulator.
 *
 * - @SpringBootApplication é uma anotação composta que ativa:
 * - @Configuration: define a classe como fonte de beans Spring
 * - @EnableAutoConfiguration: configura automaticamente o Spring com base nas dependências
 * - @ComponentScan: escaneia todos os pacotes abaixo de com.pixsimulator
 */

@SpringBootApplication
public class PixSimulatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(PixSimulatorApplication.class, args);
	}

}
