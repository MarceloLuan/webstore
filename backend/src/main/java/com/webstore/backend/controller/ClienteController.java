package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.ClienteCadastroRequest;
import com.webstore.backend.controller.dto.ClienteLoginRequest;
import com.webstore.backend.controller.dto.ClienteResponse;
import com.webstore.backend.controller.dto.LoginResponse;
import com.webstore.backend.controller.dto.UsuarioResponse;
import com.webstore.backend.model.Cliente;
import com.webstore.backend.service.ClienteService;
import com.webstore.backend.repository.UsuarioRepository;
import com.webstore.backend.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ClienteController {

    private final ClienteService clienteService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UsuarioRepository usuarioRepository;

    public ClienteController(ClienteService clienteService,
                             AuthenticationManager authenticationManager,
                             JwtUtil jwtUtil,
                             UsuarioRepository usuarioRepository) {
        this.clienteService = clienteService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.usuarioRepository = usuarioRepository;
    }

    @PostMapping("/clientes")
    public ResponseEntity<ClienteResponse> cadastrar(@RequestBody ClienteCadastroRequest request) {
        Cliente cliente = new Cliente();
        cliente.setNome(request.getNome());
        cliente.setEmail(request.getEmail());
        cliente.setTelefone(request.getTelefone());
        cliente.setSenha(request.getSenha());

        Cliente clienteSalvo = clienteService.cadastrar(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteResponse.from(clienteSalvo));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody ClienteLoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getSenha())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails);

        return usuarioRepository.findByEmail(userDetails.getUsername())
                .map(usuario -> ResponseEntity.ok(new LoginResponse(token, UsuarioResponse.from(usuario))))
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }
}
