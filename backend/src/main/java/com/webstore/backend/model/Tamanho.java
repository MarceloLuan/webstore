package com.webstore.backend.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Tamanho {
    SIZE_36("36"),
    SIZE_38("38"),
    SIZE_40("40"),
    SIZE_42("42"),
    SIZE_44("44"),
    P("P"),
    M("M"),
    G("G"),
    GG("GG"),
    G1("G1"),
    G2("G2"),
    UNICO("Tamanho Único");

    private final String label;

    Tamanho(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static Tamanho fromLabel(String label) {
        if (label == null) return null;
        for (Tamanho t : values()) {
            if (t.label.equalsIgnoreCase(label.trim())) {
                return t;
            }
        }
        // try matching by enum name
        try {
            return Tamanho.valueOf(label.trim().toUpperCase().replace(" ", "_").replace("-","_").replace("+","_"));
        } catch (Exception ex) {
            return null;
        }
    }
}

