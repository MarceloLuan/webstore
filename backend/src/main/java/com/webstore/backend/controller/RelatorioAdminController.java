package com.webstore.backend.controller;

import com.webstore.backend.controller.dto.RelatorioAdminResponse;
import com.webstore.backend.model.Categoria;
import com.webstore.backend.model.PedidoStatus;
import com.webstore.backend.service.RelatorioAdminService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/relatorios")
public class RelatorioAdminController {
    private final RelatorioAdminService relatorios;

    public RelatorioAdminController(RelatorioAdminService relatorios) {
        this.relatorios = relatorios;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public RelatorioAdminResponse gerar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) PedidoStatus status,
            @RequestParam(required = false) Categoria categoria) {
        return relatorios.gerar(inicio, fim, status, categoria);
    }
}
