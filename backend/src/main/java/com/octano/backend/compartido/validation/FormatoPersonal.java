package com.octano.backend.compartido.validation;

public final class FormatoPersonal {
    private FormatoPersonal() { }

    public static final String NOMBRE = "\\p{L}[\\p{L}\\p{M}]*(?:[ .'’-]\\p{L}[\\p{L}\\p{M}]*)*";
    public static final String TELEFONO = "(?=(?:[^0-9]*[0-9]){10,15}[^0-9]*$)[+0-9() .-]{10,30}";
}
