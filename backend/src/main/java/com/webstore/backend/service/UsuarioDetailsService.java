package com.webstore.backend.service;

import com.webstore.backend.model.Administrador;
import com.webstore.backend.model.Usuario;
import com.webstore.backend.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado."));

        String role = usuario instanceof Administrador ? "ADMIN" : "CLIENTE";

        return User.withUsername(usuario.getEmail())
                .password(usuario.getSenha())
                .roles(role)
                .disabled(!usuario.getAtivo())
                .build();
    }
}

