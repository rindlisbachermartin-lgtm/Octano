package com.octano.backend.turnos.service;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.clientes.repository.ClienteRepository;
import com.octano.backend.ordenes.service.OrdenTrabajoService;
import com.octano.backend.presupuestos.repository.PresupuestoRepository;
import com.octano.backend.presupuestos.entity.Presupuesto;
import com.octano.backend.compartido.exception.*;
import com.octano.backend.talleres.repository.TallerRepository;
import com.octano.backend.turnos.dto.TurnosDatos.*;
import com.octano.backend.turnos.entity.Turno;
import com.octano.backend.turnos.entity.Turno.Estado;
import com.octano.backend.turnos.exception.SuperposicionTurnoException;
import com.octano.backend.turnos.repository.TurnoRepository;
import com.octano.backend.usuarios.entity.Rol;
import com.octano.backend.vehiculos.repository.VehiculoRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TurnoService {
    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private final TurnoRepository turnos;
    private final TallerRepository talleres;
    private final ClienteRepository clientes;
    private final VehiculoRepository vehiculos;
    private final SesionActual sesion;
    private final PresupuestoRepository presupuestos;
    private final OrdenTrabajoService ordenes;
    public TurnoService(TurnoRepository turnos, TallerRepository talleres, ClienteRepository clientes,
                        VehiculoRepository vehiculos, PresupuestoRepository presupuestos, SesionActual sesion, OrdenTrabajoService ordenes) {
        this.ordenes = ordenes;
        this.turnos = turnos; this.talleres = talleres; this.clientes = clientes; this.vehiculos = vehiculos; this.sesion = sesion; this.presupuestos = presupuestos;
    }
    public Pagina listar(LocalDate desde, LocalDate hasta, Long clienteId, Long vehiculoId, Estado estado, int pagina, int tamano) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) throw new DatosInvalidosException("La fecha desde no puede ser posterior a la fecha hasta.");
        long taller = sesion.tallerId();
        long total = turnos.contar(taller, desde, hasta, clienteId, vehiculoId, estado);
        var contenido = turnos.listar(taller, desde, hasta, clienteId, vehiculoId, estado, pagina, tamano).stream().map(Respuesta::desde).toList();
        return new Pagina(contenido, pagina, tamano, total, (total + tamano - 1) / tamano);
    }
    public Respuesta obtener(long id) { return Respuesta.desde(buscar(id, false)); }
    public Disponibilidad disponibilidad(LocalDate fecha, LocalTime inicio, LocalTime fin, Long excluir, Long vehiculoId) {
        validarHorario(fecha, inicio, fin);
        if (excluir != null) buscar(excluir, false);
        if (vehiculoId != null) {
            var vehiculo = vehiculos.buscar(vehiculoId, sesion.tallerId(), false)
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo solicitado."));
            if (!vehiculo.getActivo()) throw new DatosInvalidosException("El vehículo está inactivo.");
        }
        var conflictos = turnos.conflictos(sesion.tallerId(), fecha, inicio, fin, excluir);
        List<Conflicto> delVehiculo = vehiculoId == null ? List.of() : turnos.turnosDelVehiculo(sesion.tallerId(), vehiculoId, fecha, excluir);
        boolean yaTieneTurno = !delVehiculo.isEmpty();
        return new Disponibilidad(conflictos.isEmpty() && !yaTieneTurno, !conflictos.isEmpty() && !yaTieneTurno,
                yaTieneTurno, conflictos, delVehiculo);
    }
    @Transactional public Respuesta guardar(Long id, Solicitud solicitud) {
        validarHorario(solicitud.fecha(), solicitud.horaInicio(), solicitud.horaFin());
        var usuario = sesion.usuario();
        boolean admin = usuario.getTipoUsuarioId() == Rol.ADMIN.id();
        boolean confirmar = Boolean.TRUE.equals(solicitud.confirmarSuperposicion());
        if (confirmar && !admin) throw new AccessDeniedException("Solo un administrador puede confirmar una superposición.");
        // ponytail: serializamos cambios de agenda por taller; bloqueos por recurso si luego se asignan puestos o mecánicos.
        talleres.findLockedById(usuario.getTallerId()).orElseThrow();
        if (id != null && !buscar(id, true).programado()) throw new DatosInvalidosException("El turno ya no está programado. Creá uno nuevo si corresponde.");
        var vehiculo = vehiculos.buscar(solicitud.vehiculoId(), usuario.getTallerId(), true)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo solicitado."));
        var cliente = clientes.findByIdAndTallerId(solicitud.clienteId(), usuario.getTallerId(), true)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente solicitado."));
        if (!vehiculo.getActivo() || !cliente.getActivo()) throw new DatosInvalidosException("Seleccioná un cliente y un vehículo activos.");
        if (!vehiculo.getClienteId().equals(cliente.getId())) throw new DatosInvalidosException("El vehículo no pertenece al cliente seleccionado.");
        if (solicitud.presupuestoId() != null) {
            var presupuesto = presupuestos.buscar(solicitud.presupuestoId(), usuario.getTallerId(), true)
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el presupuesto solicitado."));
            if (presupuesto.estado() == Presupuesto.Estado.RECHAZADO || presupuesto.estado() == Presupuesto.Estado.ANULADO) {
                throw new DatosInvalidosException("El presupuesto rechazado o anulado no puede recibir un turno.");
            }
            if (!presupuesto.clienteId().equals(cliente.getId()) || !presupuesto.vehiculoId().equals(vehiculo.getId())) {
                throw new DatosInvalidosException("El presupuesto no corresponde al cliente y vehículo seleccionados.");
            }
            if (presupuestos.turnoActivo(presupuesto.id(), usuario.getTallerId(), id).isPresent()) {
                throw new DatosInvalidosException("El presupuesto ya tiene un turno activo.");
            }
            if (id == null && ordenes.presupuestoOcupado(presupuesto.id())) throw new DatosInvalidosException("El presupuesto ya tiene una orden activa.");
        }
        if (turnos.vehiculoConTurno(usuario.getTallerId(), vehiculo.getId(), solicitud.fecha(), id)) {
            throw new DatosInvalidosException("Este vehículo ya tiene un turno activo para ese día. Editá el existente o elegí otra fecha.");
        }
        var conflictos = turnos.conflictos(usuario.getTallerId(), solicitud.fecha(), solicitud.horaInicio(), solicitud.horaFin(), id);
        if (!conflictos.isEmpty() && !confirmar) throw new SuperposicionTurnoException(conflictos, admin);
        long turnoId = turnos.guardar(id, usuario.getTallerId(), usuario.getId(), solicitud, !conflictos.isEmpty());
        ordenes.sincronizarTurno(buscar(turnoId, false));
        return obtener(turnoId);
    }
    @Transactional public Respuesta estado(long id, Estado estado) {
        talleres.findLockedById(sesion.tallerId()).orElseThrow();
        Turno turno = buscar(id, true);
        if (estado == turno.estado()) return Respuesta.desde(turno);
        if (!turno.programado() || estado == Estado.PROGRAMADO || estado == Estado.FINALIZADO) throw new DatosInvalidosException("El cambio de estado no está permitido.");
        LocalDateTime fin = turno.horaFin() == null ? turno.fecha().plusDays(1).atStartOfDay()
                : turno.fecha().atTime(turno.horaFin());
        LocalDateTime ahora = LocalDateTime.now(ZONA);
        if (estado == Estado.NO_ASISTIO && fin.isAfter(ahora)) throw new DatosInvalidosException("El horario del turno todavía no terminó.");
        if (estado == Estado.CONFIRMADO && !fin.isAfter(ahora)) throw new DatosInvalidosException("El horario del turno ya terminó.");
        turnos.estado(id, sesion.tallerId(), sesion.usuarioId(), estado);
        ordenes.sincronizarTurno(buscar(id, false));
        return obtener(id);
    }
    private Turno buscar(long id, boolean bloquear) {
        return turnos.buscar(id, sesion.tallerId(), bloquear).orElseThrow(() -> new RecursoNoEncontradoException("No existe el turno solicitado."));
    }
    private void validarHorario(LocalDate fecha, LocalTime inicio, LocalTime fin) {
        if (fin != null && !fin.isAfter(inicio)) throw new DatosInvalidosException("La hora de fin debe ser posterior a la hora de inicio, dentro del mismo día.");
        if (inicio.getSecond() != 0 || inicio.getNano() != 0
                || (fin != null && (fin.getSecond() != 0 || fin.getNano() != 0))) {
            throw new DatosInvalidosException("Ingresá los horarios con precisión de minutos, sin segundos.");
        }
        if (fecha.getYear() > 9999 || !LocalDateTime.of(fecha, inicio).isAfter(LocalDateTime.now(ZONA))) {
            throw new DatosInvalidosException("El turno debe comenzar en una fecha y hora futuras válidas.");
        }
    }
}
