package com.webstore.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Categoria {
    VESTIDO("Vestido"),
    BLUSA("Blusa"),
    CAMISETA("Camiseta"),
    CROPPED("Cropped"),
    CALCAS("Calças"),
    SHORTS("Shorts"),
    SAIA("Saia"),
    JAQUETA("Jaqueta"),
    CASACO("Casaco"),
    CONJUNTO("Conjunto"),
    MODA_INTIMA("Moda Íntima"),
    MACACAO("Macacão"),
    ACESSORIO("Acessório");

    private final String label;

    Categoria(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static Categoria fromLabel(String label) {
        if (label == null) return null;
        for (Categoria c : values()) {
            if (c.label.equalsIgnoreCase(label.trim())) {
                return c;
            }
        }
        // try matching by enum name
        try {
            return Categoria.valueOf(label.trim().toUpperCase().replace(" ", "_").replace("Á","A").replace("Í","I"));
        } catch (Exception ex) {
            return null;
        }
    }
}

