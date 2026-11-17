package com.octano.backend.usuarios.entity;

import java.time.LocalDate;

public class Usuario {
    private Long id;
    private Long tallerId;
    private String email;
    private String passwordHash;
    private Short tipoUsuarioId;
    private Long mecanicoId;
    private boolean activo;
    private boolean dueno;
    private int tokenVersion;
    private String nombre;
    private String apellido;
    private String telefono;
    private LocalDate fechaInicio;

    public void completarDatosPersonales(String nombre, String apellido, String telefono, LocalDate fechaInicio) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.telefono = telefono;
        this.fechaInicio = fechaInicio;
    }

    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getTelefono() { return telefono; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public int getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(int value) { this.tokenVersion = value; }
    public void revocarTokens() { tokenVersion++; }
    public Usuario() { }
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getTallerId() { return tallerId; }
    public void setTallerId(Long value) { this.tallerId = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { this.email = value; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String value) { this.passwordHash = value; }
    public Short getTipoUsuarioId() { return tipoUsuarioId; }
    public void setTipoUsuarioId(Short value) { this.tipoUsuarioId = value; }
    public Long getMecanicoId() { return mecanicoId; }
    public void setMecanicoId(Long value) { this.mecanicoId = value; }
    public boolean getActivo() { return activo; }
    public void setActivo(boolean value) { this.activo = value; }
    public boolean getDueno() { return dueno; }
    public void setDueno(boolean value) { this.dueno = value; }
}
