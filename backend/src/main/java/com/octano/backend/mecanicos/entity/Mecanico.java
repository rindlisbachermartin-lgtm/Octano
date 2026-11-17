package com.octano.backend.mecanicos.entity;
import java.time.LocalDate;

public class Mecanico {
    private Long id;
    private Long tallerId;
    private String nombre;
    private String apellido;
    private String telefono;
    private String calle;
    private String cuit;
    private LocalDate fechaInicio;
    private boolean activo;
    public Mecanico() { }
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getTallerId() { return tallerId; }
    public void setTallerId(Long value) { this.tallerId = value; }
    public String getNombre() { return nombre; }
    public void setNombre(String value) { this.nombre = value; }
    public String getApellido() { return apellido; }
    public void setApellido(String value) { this.apellido = value; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String value) { this.telefono = value; }
    public String getCalle() { return calle; }
    public void setCalle(String value) { this.calle = value; }
    public String getCuit() { return cuit; }
    public void setCuit(String value) { this.cuit = value; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate value) { this.fechaInicio = value; }
    public boolean getActivo() { return activo; }
    public void setActivo(boolean value) { this.activo = value; }
}
