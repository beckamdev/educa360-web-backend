package com.educa360.backend;

import com.educa360.backend.config.ZonaHoraria;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		// Los contenedores (Railway/Docker) arrancan en UTC. Fijar la zona antes
		// del contexto hace que logs y cualquier fecha no explícita usen hora de Perú.
		TimeZone.setDefault(TimeZone.getTimeZone(ZonaHoraria.APP));
		SpringApplication.run(BackendApplication.class, args);
	}

}
