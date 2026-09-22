package com.webstore.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.web.client.RestClient;

@Configuration
public class PagamentoHttpConfig {
    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    public RestClient.Builder mercadoPagoRestClientBuilder() {
        return RestClient.builder();
    }
}
