package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.ClienteCadastroRequest;
import com.webstore.backend.controller.dto.ClienteLoginRequest;
import com.webstore.backend.controller.dto.ClienteResponse;
import com.webstore.backend.controller.dto.LoginResponse;
import com.webstore.backend.controller.dto.UsuarioResponse;
import com.webstore.backend.model.Cliente;
import com.webstore.backend.service.ClienteService;
import com.webstore.backend.service.ConfirmacaoEmailService;
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
    private final ConfirmacaoEmailService confirmacaoEmailService;

    public ClienteController(ClienteService clienteService,
                             AuthenticationManager authenticationManager,
                             JwtUtil jwtUtil,
                             UsuarioRepository usuarioRepository,
                             ConfirmacaoEmailService confirmacaoEmailService) {
        this.clienteService = clienteService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.usuarioRepository = usuarioRepository;
        this.confirmacaoEmailService = confirmacaoEmailService;
    }

    @PostMapping("/clientes")
    public ResponseEntity<ClienteResponse> cadastrar(@RequestBody ClienteCadastroRequest request) {
        Cliente cliente = new Cliente();
        cliente.setNome(request.getNome());
        cliente.setEmail(request.getEmail());
        cliente.setTelefone(request.getTelefone());
        cliente.setSenha(request.getSenha());

        Cliente clienteSalvo = clienteService.cadastrar(cliente, request.getConfirmacaoSenha());
        confirmacaoEmailService.enviarParaNovoCadastro(clienteSalvo);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClienteResponse.from(clienteSalvo));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody ClienteLoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getSenha())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return usuarioRepository.findByEmail(userDetails.getUsername())
                .map(usuario -> {
                    if (!Boolean.TRUE.equals(usuario.getEmailConfirmado())) {
                        throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Confirme seu e-mail antes de entrar na conta.");
                    }
                    String token = jwtUtil.generateToken(userDetails, usuario.getVersaoSessao());
                    return ResponseEntity.ok(new LoginResponse(token, UsuarioResponse.from(usuario)));
                })
                .orElseThrow(() -> new IllegalStateException("Usuário autenticado não encontrado."));
    }
}
