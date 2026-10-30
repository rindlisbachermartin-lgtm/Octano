package com.octano.backend.usuarios.repository;

import com.octano.backend.usuarios.entity.Usuario;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Usuario> findLockedById(long id);
    Optional<Usuario> findByMecanicoId(long mecanicoId);
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically=true, flushAutomatically=true)
    @org.springframework.data.jpa.repository.Query("UPDATE Usuario u SET u.tokenVersion=u.tokenVersion+1 WHERE u.id=:id")
    void revocarTokens(long id);
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByIdAndTallerId(long id, long tallerId);
    List<Usuario> findByTallerIdOrderById(long tallerId);
    long countByTallerIdAndTipoUsuarioIdAndActivoTrue(long tallerId, short tipoUsuarioId);
}
