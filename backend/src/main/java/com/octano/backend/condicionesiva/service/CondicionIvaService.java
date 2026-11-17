package com.octano.backend.condicionesiva.service;

import com.octano.backend.compartido.exception.RecursoNoEncontradoException;
import com.octano.backend.condicionesiva.dto.CondicionIvaRespuesta;
import com.octano.backend.condicionesiva.repository.CondicionIvaRepository;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CondicionIvaService {
    private final CondicionIvaRepository condiciones;

    public CondicionIvaService(CondicionIvaRepository condiciones) { this.condiciones = condiciones; }

    public List<CondicionIvaRespuesta> listar() {
        return condiciones.findAll().stream().map(CondicionIvaRespuesta::desde).toList();
    }

    public CondicionIvaRespuesta obtener(long id) {
        return condiciones.findById(id).map(CondicionIvaRespuesta::desde)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la condición de IVA solicitada."));
    }
}
