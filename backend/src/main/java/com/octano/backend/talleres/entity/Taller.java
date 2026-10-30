package com.octano.backend.talleres.entity;

import com.octano.backend.condicionesiva.entity.CondicionIva;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "taller")
public class Taller {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;
    @Column(nullable = false, length = 150)
    private String calle;
    @Column(nullable = false)
    private Integer altura;
    @Column(nullable = false, length = 30)
    private String telefono;
    @Column(nullable = false, length = 11)
    private String cuit;
    @Column(name = "ingresos_brutos", length = 40)
    private String ingresosBrutos;
    @Column(name = "inicio_actividades", nullable = false)
    private LocalDate inicioActividades;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "condicion_iva_id", nullable = false)
    private CondicionIva condicionIva;

    protected Taller() { }

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
    public String getNombre() { return nombre; }
    public String getCalle() { return calle; }
    public Integer getAltura() { return altura; }
    public String getTelefono() { return telefono; }
    public String getCuit() { return cuit; }
    public String getIngresosBrutos() { return ingresosBrutos; }
    public LocalDate getInicioActividades() { return inicioActividades; }
    public CondicionIva getCondicionIva() { return condicionIva; }
}
