package com.octano.backend.vehiculos.entity;
import java.time.Instant;

public class Vehiculo {
    private Long id;
    private Long tallerId;
    private Long clienteId;
    private Long modeloId;
    private Long versionId;
    private String patente;
    private Integer anio;
    private Integer kilometraje;
    private Long marcaId;
    private String marca;
    private String modelo;
    private String version;
    private String clienteNombre;
    private String codigoQr;
    private boolean activo;
    private Instant fechaModif;
    private Long usuarioModificacionId;
    public Vehiculo() { }
    public Long getId() { return id; }
    public void setId(Long value) { this.id=value; }
    public Long getTallerId() { return tallerId; }
    public void setTallerId(Long value) { this.tallerId=value; }
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long value) { this.clienteId=value; }
    public Long getModeloId() { return modeloId; }
    public void setModeloId(Long value) { this.modeloId=value; }
    public Long getVersionId() { return versionId; }
    public void setVersionId(Long value) { this.versionId=value; }
    public String getPatente() { return patente; }
    public void setPatente(String value) { this.patente=value; }
    public Integer getAnio() { return anio; }
    public void setAnio(Integer value) { this.anio=value; }
    public Integer getKilometraje() { return kilometraje; }
    public void setKilometraje(Integer value) { this.kilometraje=value; }
    public Long getMarcaId() { return marcaId; }
    public void setMarcaId(Long value) { this.marcaId=value; }
    public String getMarca() { return marca; }
    public void setMarca(String value) { this.marca=value; }
    public String getModelo() { return modelo; }
    public void setModelo(String value) { this.modelo=value; }
    public String getVersion() { return version; }
    public void setVersion(String value) { this.version=value; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String value) { this.clienteNombre=value; }
    public String getCodigoQr() { return codigoQr; }
    public void setCodigoQr(String value) { this.codigoQr=value; }
    public boolean getActivo() { return activo; }
    public void setActivo(boolean value) { this.activo=value; }
    public Instant getFechaModif() { return fechaModif; }
    public void setFechaModif(Instant value) { this.fechaModif=value; }
    public Long getUsuarioModificacionId() { return usuarioModificacionId; }
    public void setUsuarioModificacionId(Long value) { this.usuarioModificacionId=value; }
}
