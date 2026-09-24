package com.webstore.backend;

import com.webstore.backend.model.Categoria;
import com.webstore.backend.model.PedidoStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Collectors;

@Configuration
public class DatabaseSchemaUpdater {

    @Bean
    public CommandLineRunner atualizarConstraintCategoriaProduto(DataSource dataSource) {
        return args -> {
            try (Connection connection = dataSource.getConnection()) {
                String databaseProduct = connection.getMetaData().getDatabaseProductName();
                if (databaseProduct == null || !databaseProduct.toLowerCase().contains("postgresql")) {
                    return;
                }

                String allowedValues = Arrays.stream(Categoria.values())
                        .map(Categoria::name)
                        .map(value -> "'" + value + "'")
                        .collect(Collectors.joining(", "));
                String orderStatuses = Arrays.stream(PedidoStatus.values())
                        .map(value -> "'" + value.name() + "'").collect(Collectors.joining(", "));

                connection.setAutoCommit(false);
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE produtos ADD COLUMN IF NOT EXISTS codigo varchar(60)");
                    statement.executeUpdate("UPDATE produtos SET codigo = 'PROD-' || id WHERE codigo IS NULL OR btrim(codigo) = ''");
                    statement.executeUpdate("ALTER TABLE produtos ALTER COLUMN codigo SET NOT NULL");
                    statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS ux_produtos_codigo ON produtos (codigo)");
                    statement.executeUpdate("ALTER TABLE produtos DROP CONSTRAINT IF EXISTS produtos_categoria_check");
                    statement.executeUpdate(
                            "ALTER TABLE produtos ADD CONSTRAINT produtos_categoria_check CHECK (categoria IN (" + allowedValues + "))"
                    );
                    // Hibernate update não amplia CHECKs de enums já existentes.
                    statement.executeUpdate("ALTER TABLE pedidos DROP CONSTRAINT IF EXISTS pedidos_status_check");
                    statement.executeUpdate("ALTER TABLE pedidos ADD CONSTRAINT pedidos_status_check CHECK (status IN (" + orderStatuses + "))");
                    statement.executeUpdate("ALTER TABLE pedidos DROP CONSTRAINT IF EXISTS ck_pedido_recebimento");
                    statement.executeUpdate("ALTER TABLE pedidos ADD CONSTRAINT ck_pedido_recebimento CHECK (" + com.webstore.backend.model.Pedido.RECEBIMENTO_CONSTRAINT + ")");
                    statement.executeUpdate("ALTER TABLE tentativas_pagamento DROP CONSTRAINT IF EXISTS tentativas_pagamento_status_check");
                    statement.executeUpdate("ALTER TABLE tentativas_pagamento ADD CONSTRAINT tentativas_pagamento_status_check CHECK (status IN (" + orderStatuses + "))");
                    statement.executeUpdate("UPDATE tentativas_pagamento SET status = CASE status_provedor WHEN 'refunded' THEN 'REEMBOLSADO' WHEN 'charged_back' THEN 'CHARGEBACK' END WHERE status = 'CANCELADO' AND status_provedor IN ('refunded', 'charged_back')");
                    statement.executeUpdate("UPDATE pedidos SET status = CASE mercado_pago_status WHEN 'refunded' THEN 'REEMBOLSADO' WHEN 'charged_back' THEN 'CHARGEBACK' END WHERE status = 'CANCELADO' AND mercado_pago_status IN ('refunded', 'charged_back')");
                    statement.executeUpdate("ALTER TABLE produto_tamanhos ADD COLUMN IF NOT EXISTS quantidade_reservada integer NOT NULL DEFAULT 0");
                    statement.executeUpdate("ALTER TABLE produto_tamanhos DROP CONSTRAINT IF EXISTS ck_produto_tamanhos_estoque");
                    statement.executeUpdate("ALTER TABLE produto_tamanhos ADD CONSTRAINT ck_produto_tamanhos_estoque CHECK (quantidade >= 0 AND quantidade_reservada >= 0 AND quantidade_reservada <= quantidade)");
                    connection.commit();
                } catch (Exception failure) {
                    connection.rollback();
                    throw failure;
                }
            }
        };
    }
}
