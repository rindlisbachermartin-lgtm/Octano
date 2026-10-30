package com.octano.backend.usuarios.repository;

import com.octano.backend.usuarios.entity.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioRepository {
    private static final String CONSULTA = "SELECT id, taller_id, email, password_hash, tipo_usuario_id, mecanico_id, activo, token_version FROM octano.usuario";
    private static final RowMapper<Usuario> FILA = (rs, numero) -> {
        Usuario usuario = new Usuario();
        usuario.setId(rs.getLong("id")); usuario.setTallerId(rs.getLong("taller_id"));
        usuario.setEmail(rs.getString("email")); usuario.setPasswordHash(rs.getString("password_hash"));
        usuario.setTipoUsuarioId(rs.getShort("tipo_usuario_id")); usuario.setMecanicoId(rs.getObject("mecanico_id", Long.class));
        usuario.setActivo(rs.getBoolean("activo")); usuario.setTokenVersion(rs.getInt("token_version"));
        return usuario;
    };
    private final JdbcTemplate jdbc;
    public UsuarioRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Usuario> findById(long id) {
        return jdbc.query(CONSULTA + " WHERE id = ?", FILA, id).stream().findFirst();
    }
    public Optional<Usuario> findLockedById(long id) {
        return jdbc.query(CONSULTA + " WHERE id = ? FOR UPDATE", FILA, id).stream().findFirst();
    }
    public Optional<Usuario> findByMecanicoId(long id) {
        return jdbc.query(CONSULTA + " WHERE mecanico_id = ?", FILA, id).stream().findFirst();
    }
    public Optional<Usuario> findByEmail(String email) {
        return jdbc.query(CONSULTA + " WHERE email = ?", FILA, email).stream().findFirst();
    }
    public Optional<Usuario> findByIdAndTallerId(long id, long tallerId) {
        return jdbc.query(CONSULTA + " WHERE id = ? AND taller_id = ?", FILA, id, tallerId).stream().findFirst();
    }
    public List<Usuario> findByTallerIdOrderById(long tallerId) {
        return jdbc.query(CONSULTA + " WHERE taller_id = ? ORDER BY id", FILA, tallerId);
    }
    public long countByTallerIdAndTipoUsuarioIdAndActivoTrue(long tallerId, short tipoUsuarioId) {
        return jdbc.queryForObject("SELECT count(*) FROM octano.usuario WHERE taller_id = ? AND tipo_usuario_id = ? AND activo = TRUE",
                Long.class, tallerId, tipoUsuarioId);
    }
    public void revocarTokens(long id) {
        jdbc.update("UPDATE octano.usuario SET token_version = token_version + 1 WHERE id = ?", id);
    }
    public Usuario save(Usuario usuario) {
        if (usuario.getId() == null) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO octano.usuario(taller_id, email, password_hash, tipo_usuario_id, mecanico_id, activo, token_version)
                    VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, usuario.getTallerId(), usuario.getEmail(), usuario.getPasswordHash(),
                    usuario.getTipoUsuarioId(), usuario.getMecanicoId(), usuario.getActivo(), usuario.getTokenVersion());
            usuario.setId(id);
        } else {
            jdbc.update("""
                    UPDATE octano.usuario SET email = ?, password_hash = ?, tipo_usuario_id = ?, mecanico_id = ?,
                        activo = ?, token_version = ? WHERE id = ? AND taller_id = ?
                    """, usuario.getEmail(), usuario.getPasswordHash(), usuario.getTipoUsuarioId(), usuario.getMecanicoId(),
                    usuario.getActivo(), usuario.getTokenVersion(), usuario.getId(), usuario.getTallerId());
        }
        return usuario;
    }
}
