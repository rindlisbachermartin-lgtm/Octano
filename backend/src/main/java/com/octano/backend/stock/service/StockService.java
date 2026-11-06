package com.octano.backend.stock.service;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.compartido.exception.*;
import com.octano.backend.repuestos.entity.Repuesto;
import com.octano.backend.repuestos.repository.RepuestoRepository;
import com.octano.backend.stock.dto.StockDatos.*;
import com.octano.backend.stock.repository.StockRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StockService {
    private final RepuestoRepository repuestos;
    private final StockRepository movimientos;
    private final SesionActual sesion;
    public StockService(RepuestoRepository repuestos, StockRepository movimientos, SesionActual sesion) {
        this.repuestos = repuestos; this.movimientos = movimientos; this.sesion = sesion;
    }
    public Pagina listar(long id, int pagina, int tamano) {
        buscarRepuesto(id, false);
        long total = movimientos.contar(sesion.tallerId(), id);
        return new Pagina(movimientos.listar(sesion.tallerId(), id, pagina, tamano), pagina, tamano, total, (total + tamano - 1) / tamano);
    }
    public Movimiento obtener(long repuestoId, long id) {
        buscarRepuesto(repuestoId, false);
        return movimientos.buscar(sesion.tallerId(), repuestoId, id).orElseThrow(() -> new RecursoNoEncontradoException("No existe el movimiento solicitado."));
    }
    @Transactional public Movimiento mover(long id, Solicitud solicitud) {
        return mover(id, solicitud, true);
    }
    @Transactional public Movimiento devolverDeOrden(long id, BigDecimal cantidad, String motivo) {
        // Una devolución de material ya consumido también debe poder reponer un repuesto archivado.
        return mover(id, new Solicitud(Tipo.ENTRADA, cantidad, motivo), false);
    }
    private Movimiento mover(long id, Solicitud solicitud, boolean exigirActivo) {
        // Bloqueamos antes de leer el saldo: dos salidas simultáneas no consumen las mismas unidades.
        Repuesto repuesto = buscarRepuesto(id, true);
        if (exigirActivo && !repuesto.activo()) throw new DatosInvalidosException("Primero activá el repuesto para mover su stock.");
        if (solicitud.tipo() != Tipo.AJUSTE && solicitud.cantidad().signum() == 0) throw new DatosInvalidosException("La cantidad debe ser mayor que cero.");
        BigDecimal posterior = switch (solicitud.tipo()) {
            case ENTRADA -> repuesto.stock().add(solicitud.cantidad());
            case SALIDA -> repuesto.stock().subtract(solicitud.cantidad());
            case AJUSTE -> solicitud.cantidad();
        };
        if (posterior.signum() < 0) throw new DatosInvalidosException("No hay stock suficiente para esa salida.");
        if (posterior.compareTo(new BigDecimal("999999999999.999")) > 0) throw new DatosInvalidosException("El saldo supera la cantidad máxima admitida.");
        if (posterior.compareTo(repuesto.stock()) == 0) throw new DatosInvalidosException("El ajuste no cambia el stock actual.");
        var movimiento = movimientos.registrar(sesion.tallerId(), id, sesion.usuarioId(), solicitud, repuesto.stock(), posterior);
        repuestos.saldo(id, sesion.tallerId(), sesion.usuarioId(), posterior);
        return movimiento;
    }
    private Repuesto buscarRepuesto(long id, boolean bloquear) {
        return repuestos.buscar(id, sesion.tallerId(), bloquear).orElseThrow(() -> new RecursoNoEncontradoException("No existe el repuesto solicitado."));
    }
}
