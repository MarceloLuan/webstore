package com.webstore.backend;

import com.webstore.backend.model.Categoria;
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

                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE produtos ADD COLUMN IF NOT EXISTS codigo varchar(60)");
                    statement.executeUpdate("UPDATE produtos SET codigo = 'PROD-' || id WHERE codigo IS NULL OR btrim(codigo) = ''");
                    statement.executeUpdate("ALTER TABLE produtos ALTER COLUMN codigo SET NOT NULL");
                    statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS ux_produtos_codigo ON produtos (codigo)");
                    statement.executeUpdate("ALTER TABLE produtos DROP CONSTRAINT IF EXISTS produtos_categoria_check");
                    statement.executeUpdate(
                            "ALTER TABLE produtos ADD CONSTRAINT produtos_categoria_check CHECK (categoria IN (" + allowedValues + "))"
                    );
                }
            }
        };
    }
}
