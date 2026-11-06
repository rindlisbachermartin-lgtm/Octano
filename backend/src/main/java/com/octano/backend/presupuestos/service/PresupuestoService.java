package com.octano.backend.presupuestos.service;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.clientes.repository.ClienteRepository;
import com.octano.backend.compartido.exception.*;
import com.octano.backend.presupuestos.dto.PresupuestosDatos.*;
import com.octano.backend.presupuestos.entity.Presupuesto;
import com.octano.backend.presupuestos.entity.Presupuesto.*;
import com.octano.backend.presupuestos.repository.PresupuestoRepository;
import com.octano.backend.repuestos.repository.RepuestoRepository;
import com.octano.backend.talleres.repository.TallerRepository;
import com.octano.backend.vehiculos.repository.VehiculoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PresupuestoService {
    private static final BigDecimal MAXIMO = new BigDecimal("9999999999999999.99");
    private final PresupuestoRepository presupuestos;
    private final TallerRepository talleres;
    private final VehiculoRepository vehiculos;
    private final ClienteRepository clientes;
    private final RepuestoRepository repuestos;
    private final SesionActual sesion;
    public PresupuestoService(PresupuestoRepository presupuestos, TallerRepository talleres, VehiculoRepository vehiculos,
                              ClienteRepository clientes, RepuestoRepository repuestos, SesionActual sesion) {
        this.presupuestos = presupuestos; this.talleres = talleres; this.vehiculos = vehiculos;
        this.clientes = clientes; this.repuestos = repuestos; this.sesion = sesion;
    }
    public Pagina listar(String q, Estado estado, Long cliente, Long vehiculo, LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) throw new DatosInvalidosException("La fecha desde no puede superar la fecha hasta.");
        long taller = sesion.tallerId();
        long total = presupuestos.contar(taller, q.strip(), estado, cliente, vehiculo, desde, hasta);
        var contenido = presupuestos.listar(taller, q.strip(), estado, cliente, vehiculo, desde, hasta, pagina, tamano).stream().map(Resumen::desde).toList();
        return new Pagina(contenido, pagina, tamano, total, (total + tamano - 1) / tamano);
    }
    public Respuesta obtener(long id) {
        Presupuesto p = buscar(id, false);
        return Respuesta.desde(p, presupuestos.turnoActivo(id, p.tallerId(), null).orElse(null), presupuestos.items(id, p.tallerId()));
    }
    @Transactional public Respuesta guardar(Long id, Solicitud s) {
        // Mismo bloqueo que Turnos: una reserva y una modificación del presupuesto no se cruzan.
        talleres.findLockedById(sesion.tallerId()).orElseThrow();
        Presupuesto actual = id == null ? null : buscar(id, true);
        if (actual != null && (actual.estado() != Estado.PENDIENTE || presupuestos.turnoActivo(id, actual.tallerId(), null).isPresent())) {
            throw new DatosInvalidosException("Solo se puede editar un presupuesto pendiente y sin turno activo.");
        }
        var vehiculo = vehiculos.buscar(s.vehiculoId(), sesion.tallerId(), true)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo solicitado."));
        var cliente = clientes.findByIdAndTallerId(s.clienteId(), sesion.tallerId(), true)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente solicitado."));
        if (!vehiculo.getActivo() || !cliente.getActivo()) throw new DatosInvalidosException("Seleccioná un cliente y un vehículo activos.");
        if (!vehiculo.getClienteId().equals(cliente.getId())) throw new DatosInvalidosException("El vehículo no pertenece al cliente seleccionado.");
        List<Detalle> items = calcularItems(s.items());
        BigDecimal totalRepuestos = importe(items.stream().map(Detalle::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal subtotal = importe(totalRepuestos.add(s.manoObra()));
        BigDecimal iva = importe(subtotal.multiply(s.porcentajeIva().movePointLeft(2)));
        BigDecimal total = importe(subtotal.add(iva));
        Presupuesto nuevo = new Presupuesto(id, sesion.tallerId(), s.clienteId(), s.vehiculoId(),
                actual == null ? LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")) : actual.fecha(),
                s.descripcion(), importe(s.manoObra()), s.porcentajeIva(), totalRepuestos, subtotal, iva, total,
                s.tiposTrabajo().stream().distinct().toList(), Estado.PENDIENTE, null, null, null, null);
        return obtener(presupuestos.guardar(nuevo, sesion.usuarioId(), items));
    }
    private List<Detalle> calcularItems(List<ItemSolicitud> solicitudes) {
        List<Detalle> items = new ArrayList<>();
        var usados = new HashSet<Long>();
        for (var s : solicitudes) {
            String codigo = null;
            String descripcion = s.descripcion();
            BigDecimal precio = s.precioUnitario();
            if (s.repuestoId() != null) {
                if (!usados.add(s.repuestoId())) throw new DatosInvalidosException("No repitas un repuesto: aumentá su cantidad en el mismo renglón.");
                var repuesto = repuestos.buscar(s.repuestoId(), sesion.tallerId(), true)
                        .orElseThrow(() -> new RecursoNoEncontradoException("No existe uno de los repuestos solicitados."));
                if (!repuesto.activo()) throw new DatosInvalidosException("No se puede agregar un repuesto inactivo.");
                codigo = repuesto.codigoOem(); descripcion = repuesto.descripcion();
                if (precio == null) precio = repuesto.precioPublico();
            } else if (descripcion == null || descripcion.isBlank() || precio == null) {
                throw new DatosInvalidosException("Los renglones manuales necesitan descripción y precio unitario.");
            }
            precio = importe(precio);
            items.add(new Detalle(items.size() + 1, s.repuestoId(), codigo, descripcion, s.cantidad(), precio, importe(precio.multiply(s.cantidad()))));
        }
        return items;
    }
    private BigDecimal importe(BigDecimal valor) {
        BigDecimal redondeado = valor.setScale(2, RoundingMode.HALF_UP);
        if (redondeado.compareTo(MAXIMO) > 0) throw new DatosInvalidosException("El importe supera el máximo admitido.");
        return redondeado;
    }
    @Transactional public Respuesta estado(long id, Estado estado) {
        talleres.findLockedById(sesion.tallerId()).orElseThrow();
        Presupuesto p = buscar(id, true);
        if (p.estado() == estado) return obtener(id);
        if (p.estado() == Estado.ANULADO || estado == Estado.PENDIENTE || (p.estado() != Estado.PENDIENTE && estado != Estado.ANULADO)) {
            throw new DatosInvalidosException("El cambio de estado no está permitido.");
        }
        if (estado == Estado.RECHAZADO || estado == Estado.ANULADO) presupuestos.cancelarTurnos(id, p.tallerId(), sesion.usuarioId());
        presupuestos.estado(id, p.tallerId(), sesion.usuarioId(), estado);
        return obtener(id);
    }
    private Presupuesto buscar(long id, boolean bloquear) {
        return presupuestos.buscar(id, sesion.tallerId(), bloquear).orElseThrow(() -> new RecursoNoEncontradoException("No existe el presupuesto solicitado."));
    }
}
