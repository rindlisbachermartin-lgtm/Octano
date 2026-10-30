package com.octano.backend.condicionesiva.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "condicion_iva")
public class CondicionIva {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, unique = true)
    private String nombre;

    protected CondicionIva() { }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
}
