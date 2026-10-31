package com.octano.backend.clientes.entity;
import java.time.Instant;

public class Cliente {
    private Long id;
    private Long tallerId;
    private String nombre;
    private String documento;
    private String telefono;
    private String email;
    private String calle;
    private Integer altura;
    private Long condicionIvaId;
    private boolean activo;
    private Instant fechaModif;
    private Long usuarioModificacionId;
    public Cliente() { }
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getTallerId() { return tallerId; }
    public void setTallerId(Long value) { this.tallerId=value; }
    public String getNombre() { return nombre; }
    public void setNombre(String value) { this.nombre=value; }
    public String getDocumento() { return documento; }
    public void setDocumento(String value) { this.documento=value; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String value) { this.telefono=value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { this.email=value; }
    public String getCalle() { return calle; }
    public void setCalle(String value) { this.calle=value; }
    public Integer getAltura() { return altura; }
    public void setAltura(Integer value) { this.altura=value; }
    public Long getCondicionIvaId() { return condicionIvaId; }
    public void setCondicionIvaId(Long value) { this.condicionIvaId=value; }
    public boolean getActivo() { return activo; }
    public void setActivo(boolean value) { this.activo=value; }
    public Instant getFechaModif() { return fechaModif; }
    public void setFechaModif(Instant value) { this.fechaModif=value; }
    public Long getUsuarioModificacionId() { return usuarioModificacionId; }
    public void setUsuarioModificacionId(Long value) { this.usuarioModificacionId=value; }
}
