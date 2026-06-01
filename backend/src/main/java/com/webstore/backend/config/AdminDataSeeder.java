package com.webstore.backend.config;

import com.webstore.backend.model.Administrador;
import com.webstore.backend.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminDataSeeder {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String ADMIN_PASSWORD = "123456";

    @Bean
    public CommandLineRunner seedDefaultAdmin(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (usuarioRepository.existsByEmail(ADMIN_EMAIL)) {
                return;
            }

            usuarioRepository.save(new Administrador(
                    "Admin Teste",
                    ADMIN_EMAIL,
                    "11999999999",
                    passwordEncoder.encode(ADMIN_PASSWORD)
            ));
        };
    }
}