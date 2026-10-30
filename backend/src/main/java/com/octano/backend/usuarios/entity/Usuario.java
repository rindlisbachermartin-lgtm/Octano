package com.octano.backend.usuarios.entity;

public class Usuario {
    private Long id;
    private Long tallerId;
    private String email;
    private String passwordHash;
    private Short tipoUsuarioId;
    private Long mecanicoId;
    private boolean activo;
    private int tokenVersion;
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
}
