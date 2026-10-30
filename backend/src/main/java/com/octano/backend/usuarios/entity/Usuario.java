package com.octano.backend.usuarios.entity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "usuario")
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "taller_id", nullable = false)
    private Long tallerId;
    @Column(name = "email", nullable = false, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "tipo_usuario_id", nullable = false)
    private Short tipoUsuarioId;
    @Column(name = "mecanico_id", nullable = true)
    private Long mecanicoId;
    @Column(name = "activo", nullable = false)
    private boolean activo;
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;
    public int getTokenVersion() { return tokenVersion; }
    public void revocarTokens() { tokenVersion++; }
    public Usuario() { }
    public Long getId() { return id; }
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
