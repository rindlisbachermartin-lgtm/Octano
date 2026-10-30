package com.octano.backend.talleres.entity;

import com.octano.backend.condicionesiva.entity.CondicionIva;

import java.time.LocalDate;

public class Taller {
    private Long id;

    private String nombre;
    private String calle;
    private Integer altura;
    private String telefono;
    private String cuit;
    private String ingresosBrutos;
    private LocalDate inicioActividades;

    private CondicionIva condicionIva;

    public Taller(String nombre, String calle, Integer altura, String telefono, String cuit,
                  String ingresosBrutos, LocalDate inicioActividades, CondicionIva condicionIva) {
        actualizar(nombre, calle, altura, telefono, cuit, ingresosBrutos, inicioActividades, condicionIva);
    }

    public void actualizar(String nombre, String calle, Integer altura, String telefono, String cuit,
                           String ingresosBrutos, LocalDate inicioActividades, CondicionIva condicionIva) {
        this.nombre = nombre;
        this.calle = calle;
        this.altura = altura;
        this.telefono = telefono;
        this.cuit = cuit;
        this.ingresosBrutos = ingresosBrutos;
        this.inicioActividades = inicioActividades;
        this.condicionIva = condicionIva;
    }

    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public String getNombre() { return nombre; }
    public String getCalle() { return calle; }
    public Integer getAltura() { return altura; }
    public String getTelefono() { return telefono; }
    public String getCuit() { return cuit; }
    public String getIngresosBrutos() { return ingresosBrutos; }
    public LocalDate getInicioActividades() { return inicioActividades; }
    public CondicionIva getCondicionIva() { return condicionIva; }
}
