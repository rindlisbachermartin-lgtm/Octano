package com.octano.backend.repuestos.service;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.catalogovehiculos.repository.CatalogoVehiculoRepository;
import com.octano.backend.compartido.exception.*;
import com.octano.backend.repuestos.dto.RepuestosDatos.*;
import com.octano.backend.repuestos.entity.Repuesto;
import com.octano.backend.repuestos.repository.RepuestoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RepuestoService {
    private final RepuestoRepository repuestos;
    private final CatalogoVehiculoRepository catalogo;
    private final SesionActual sesion;
    public RepuestoService(RepuestoRepository repuestos, CatalogoVehiculoRepository catalogo, SesionActual sesion) {
        this.repuestos = repuestos; this.catalogo = catalogo; this.sesion = sesion;
    }
    public Pagina listar(String q, Boolean activo, Boolean stockBajo, Long modeloId, Long versionId, int pagina, int tamano) {
        if (versionId != null) {
            var version = catalogo.version(versionId).orElseThrow(() -> new RecursoNoEncontradoException("No existe la versión seleccionada."));
            if (modeloId != null && !modeloId.equals(version.padreId())) throw new DatosInvalidosException("La versión no pertenece al modelo seleccionado.");
            modeloId = version.padreId();
        }
        long tallerId = sesion.tallerId();
        String busqueda = q.strip();
        long total = repuestos.contar(tallerId, busqueda, activo, stockBajo, modeloId, versionId);
        var contenido = repuestos.listar(tallerId, busqueda, activo, stockBajo, modeloId, versionId, pagina, tamano).stream().map(Respuesta::desde).toList();
        return new Pagina(contenido, pagina, tamano, total, (total + tamano - 1) / tamano);
    }
    public Respuesta obtener(long id) { return Respuesta.desde(buscar(id, false)); }
    @Transactional public Respuesta guardar(Long id, Solicitud s) {
        if (id != null) buscar(id, true);
        BigDecimal precio = s.precioCosto().multiply(BigDecimal.ONE.add(s.margen().movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
        long repuestoId = repuestos.guardar(id, sesion.tallerId(), sesion.usuarioId(), s, precio);
        return obtener(repuestoId);
    }
    @Transactional public Respuesta estado(long id, boolean activo) {
        buscar(id, true);
        repuestos.estado(id, sesion.tallerId(), sesion.usuarioId(), activo);
        return obtener(id);
    }
    public List<Compatibilidad> compatibilidades(long id) {
        buscar(id, false);
        return repuestos.compatibilidades(id, sesion.tallerId());
    }
    @Transactional public Compatibilidad agregarCompatibilidad(long id, CompatibilidadSolicitud s) {
        buscar(id, true);
        if (!catalogo.existeModelo(s.modeloId())) throw new RecursoNoEncontradoException("No existe el modelo seleccionado.");
        if (s.versionId() != null && catalogo.version(s.versionId()).filter(v -> v.padreId().equals(s.modeloId())).isEmpty()) {
            throw new DatosInvalidosException("La versión no pertenece al modelo seleccionado.");
        }
        return repuestos.agregar(id, sesion.tallerId(), s);
    }
    @Transactional public void quitarCompatibilidad(long id, long compatibilidadId) {
        buscar(id, true);
        if (!repuestos.quitar(id, sesion.tallerId(), compatibilidadId)) throw new RecursoNoEncontradoException("No existe la compatibilidad solicitada.");
    }
    private Repuesto buscar(long id, boolean bloquear) {
        return repuestos.buscar(id, sesion.tallerId(), bloquear).orElseThrow(() -> new RecursoNoEncontradoException("No existe el repuesto solicitado."));
    }
}
