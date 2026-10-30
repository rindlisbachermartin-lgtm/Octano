package com.octano.backend.mecanicos.repository;

import com.octano.backend.mecanicos.entity.Mecanico;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MecanicoRepository extends JpaRepository<Mecanico, Long> {
    Optional<Mecanico> findByIdAndTallerId(long id, long tallerId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Mecanico> findLockedByIdAndTallerId(long id, long tallerId);
    List<Mecanico> findByTallerIdOrderByApellidoAscNombreAsc(long tallerId);
}
