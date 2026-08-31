package com.webstore.backend.controller.dto;

public record CheckoutResponse(String checkoutUrl, String preferenceId, Long pedidoId) {
}
