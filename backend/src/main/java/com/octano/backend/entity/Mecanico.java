package com.octano.backend.entity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "mecanico")
public class Mecanico {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "taller_id", nullable = false)
    private Long tallerId;
    @Column(name = "nombre", nullable = false, length = 90)
    private String nombre;
    @Column(name = "apellido", nullable = false, length = 90)
    private String apellido;
    @Column(name = "telefono", nullable = false, length = 30)
    private String telefono;
    @Column(name = "calle", nullable = true, length = 150)
    private String calle;
    @Column(name = "cuit", nullable = true, length = 11)
    private String cuit;
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;
    @Column(name = "activo", nullable = false)
    private boolean activo;
    public Mecanico() { }
    public Long getId() { return id; }
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
