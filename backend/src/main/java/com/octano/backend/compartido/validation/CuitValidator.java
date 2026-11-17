package com.octano.backend.compartido.validation;

import com.octano.backend.compartido.exception.DatosInvalidosException;

public final class CuitValidator {
    private CuitValidator() { }

    public static void validar(String cuit) {
        if (cuit == null || !cuit.matches("[0-9]{11}")) {
            throw new DatosInvalidosException("El CUIT debe tener 11 números.");
        }
        int[] pesos = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int suma = 0;
        for (int i = 0; i < pesos.length; i++) suma += (cuit.charAt(i) - '0') * pesos[i];
        int verificador = (11 - suma % 11) % 11;
        if (verificador != cuit.charAt(10) - '0') {
            throw new DatosInvalidosException("El CUIT no tiene un dígito verificador válido.");
        }
    }
}
