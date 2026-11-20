package com.doph;

import com.doph.role.Role;
import com.doph.role.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

@SpringBootApplication
public class AsymmectricEncryptionApplication {

	public static void main(String[] args) {
		SpringApplication.run(AsymmectricEncryptionApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(final RoleRepository roleRepository){
		return args -> {
			final Optional<Role> userRole = roleRepository.findByName("USER_ROLE");
			if(userRole.isEmpty()){
				final Role role = new Role();;
				role.setName("ROLE_USER");
				roleRepository.save(role);
			}
		};
	}
}
