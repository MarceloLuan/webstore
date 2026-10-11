package com.webstore.backend.service;

import com.webstore.backend.model.Administrador;
import com.webstore.backend.model.Usuario;
import com.webstore.backend.repository.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import java.util.Locale;
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
        Usuario usuario = usuarioRepository.findByEmail(email == null ? "" : email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado."));

        if (!usuario.possuiSenhaLocal()) {
            throw new UsernameNotFoundException("Credenciais inválidas.");
        }
        return from(usuario);
    }

    public UserDetails from(Usuario usuario) {
        String role = usuario instanceof Administrador ? "ADMIN" : "CLIENTE";

        return new UserDetails() {
            public String getUsername() { return usuario.getEmail(); }
            public String getPassword() { return usuario.getSenha(); }
            public java.util.Collection<SimpleGrantedAuthority> getAuthorities() {
                return List.of(new SimpleGrantedAuthority("ROLE_" + role));
            }
            public boolean isEnabled() { return Boolean.TRUE.equals(usuario.getAtivo()); }
        };
    }
}

