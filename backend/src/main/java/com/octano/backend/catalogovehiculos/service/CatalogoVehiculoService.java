package com.octano.backend.catalogovehiculos.service;

import com.octano.backend.catalogovehiculos.dto.CatalogoDatos.*;
import com.octano.backend.catalogovehiculos.repository.CatalogoVehiculoRepository;
import com.octano.backend.compartido.exception.RecursoNoEncontradoException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogoVehiculoService {
    private final CatalogoVehiculoRepository catalogo;
    public CatalogoVehiculoService(CatalogoVehiculoRepository catalogo) { this.catalogo = catalogo; }
    public List<Opcion> marcas() { return catalogo.marcas(); }
    public List<Opcion> modelos(long marcaId) {
        if (!catalogo.existeMarca(marcaId)) throw new RecursoNoEncontradoException("No existe la marca seleccionada.");
        return catalogo.modelos(marcaId);
    }
    public List<Opcion> versiones(long modeloId) {
        validarModelo(modeloId);
        return catalogo.versiones(modeloId);
    }
    @Transactional public Opcion crearMarca(MarcaSolicitud solicitud) { return catalogo.crearMarca(solicitud.nombre()); }
    @Transactional public Opcion crearModelo(ModeloSolicitud solicitud) {
        if (!catalogo.existeMarca(solicitud.marcaId())) throw new RecursoNoEncontradoException("No existe la marca seleccionada.");
        return catalogo.crearModelo(solicitud.marcaId(), solicitud.nombre());
    }
    @Transactional public Opcion crearVersion(VersionSolicitud solicitud) {
        validarModelo(solicitud.modeloId());
        return catalogo.crearVersion(solicitud.modeloId(), solicitud.nombre());
    }
    private void validarModelo(long id) {
        if (!catalogo.existeModelo(id)) throw new RecursoNoEncontradoException("No existe el modelo seleccionado.");
    }
}
