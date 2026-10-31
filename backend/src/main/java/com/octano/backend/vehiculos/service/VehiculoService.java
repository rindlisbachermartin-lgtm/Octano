package com.octano.backend.vehiculos.service;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.catalogovehiculos.repository.CatalogoVehiculoRepository;
import com.octano.backend.clientes.entity.Cliente;
import com.octano.backend.clientes.repository.ClienteRepository;
import com.octano.backend.compartido.exception.*;
import com.octano.backend.vehiculos.dto.VehiculosDatos.*;
import com.octano.backend.vehiculos.entity.Vehiculo;
import com.octano.backend.vehiculos.repository.VehiculoRepository;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VehiculoService {
    private final VehiculoRepository vehiculos;
    private final ClienteRepository clientes;
    private final CatalogoVehiculoRepository catalogo;
    private final SesionActual sesion;
    public VehiculoService(VehiculoRepository vehiculos, ClienteRepository clientes, CatalogoVehiculoRepository catalogo, SesionActual sesion) {
        this.vehiculos = vehiculos; this.clientes = clientes; this.catalogo = catalogo; this.sesion = sesion;
    }
    public Pagina listar(Long clienteId, Boolean activo, String patente, int pagina, int tamano) {
        String filtro = patente.toUpperCase(Locale.ROOT).replaceAll("[ -]", "");
        long tallerId = sesion.tallerId();
        long total = vehiculos.contar(tallerId, clienteId, activo, filtro);
        var contenido = vehiculos.listar(tallerId, clienteId, activo, filtro, pagina, tamano).stream().map(Respuesta::desde).toList();
        return new Pagina(contenido, pagina, tamano, total, (total + tamano - 1) / tamano);
    }
    public Respuesta obtener(long id) { return Respuesta.desde(buscar(id, false)); }
    public List<Titularidad> historial(long id) {
        buscar(id, false);
        return vehiculos.historial(sesion.tallerId(), id);
    }
    @Transactional
    public Respuesta guardar(Long id, Solicitud solicitud) {
        Vehiculo v = id == null ? new Vehiculo() : buscar(id, true);
        if (solicitud.anio() > Year.now().getValue() + 1) throw new DatosInvalidosException("El año del vehículo no puede superar el próximo año.");
        if (!catalogo.existeModelo(solicitud.modeloId())) throw new RecursoNoEncontradoException("No existe el modelo seleccionado.");
        if (solicitud.versionId() != null && catalogo.version(solicitud.versionId())
                .filter(version -> version.padreId().equals(solicitud.modeloId())).isEmpty()) {
            throw new DatosInvalidosException("La versión no pertenece al modelo seleccionado.");
        }
        Cliente cliente = cliente(solicitud.clienteId());
        if (!cliente.getActivo() && !Objects.equals(v.getClienteId(), solicitud.clienteId())) {
            throw new DatosInvalidosException("Seleccioná un cliente activo.");
        }
        Long anterior = v.getClienteId();
        if (id == null) { v.setTallerId(sesion.tallerId()); v.setActivo(true); }
        v.setClienteId(solicitud.clienteId()); v.setModeloId(solicitud.modeloId()); v.setVersionId(solicitud.versionId());
        v.setPatente(solicitud.patente()); v.setAnio(solicitud.anio()); v.setKilometraje(solicitud.kilometraje());
        auditar(v);
        vehiculos.save(v);
        if (!Objects.equals(anterior, v.getClienteId())) vehiculos.registrarTitularidad(v, anterior);
        return Respuesta.desde(v);
    }
    @Transactional
    public Respuesta titular(long id, long clienteId) {
        Vehiculo v = buscar(id, true);
        if (!cliente(clienteId).getActivo()) throw new DatosInvalidosException("Seleccioná un cliente activo.");
        if (!v.getClienteId().equals(clienteId)) {
            Long anterior = v.getClienteId();
            v.setClienteId(clienteId); auditar(v); vehiculos.save(v);
            vehiculos.registrarTitularidad(v, anterior);
        }
        return Respuesta.desde(v);
    }
    @Transactional
    public Respuesta estado(long id, boolean activo) {
        Vehiculo v = buscar(id, true);
        v.setActivo(activo); auditar(v);
        return Respuesta.desde(vehiculos.save(v));
    }
    private void auditar(Vehiculo v) { v.setFechaModif(Instant.now()); v.setUsuarioModificacionId(sesion.usuarioId()); }
    private Cliente cliente(long id) {
        // La fila bloqueada impide asignar un cliente mientras otra petición lo da de baja.
        return clientes.findByIdAndTallerId(id, sesion.tallerId(), true)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente solicitado."));
    }
    private Vehiculo buscar(long id, boolean bloquear) {
        return vehiculos.buscar(id, sesion.tallerId(), bloquear)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo solicitado."));
    }
}
