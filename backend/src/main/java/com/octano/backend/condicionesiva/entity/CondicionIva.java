package com.octano.backend.condicionesiva.entity;

public class CondicionIva {
    private final Long id;
    private final String nombre;

    public CondicionIva(Long id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
}
