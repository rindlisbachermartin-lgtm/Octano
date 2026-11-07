package com.octano.backend.reportes.service;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.compartido.exception.DatosInvalidosException;
import com.octano.backend.reportes.dto.ReportesDatos.*;
import com.octano.backend.reportes.repository.ReporteRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ReporteService {
    private final ReporteRepository reportes;
    private final SesionActual sesion;
    public ReporteService(ReporteRepository reportes,SesionActual sesion) { this.reportes=reportes; this.sesion=sesion; }
    private LocalDate hoy() { return LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")); }
    private Periodo periodo(LocalDate desde,LocalDate hasta) {
        LocalDate fin=hasta==null?hoy():hasta;
        LocalDate inicio=desde==null?fin.withDayOfMonth(1):desde;
        if(inicio.isAfter(fin)) throw new DatosInvalidosException("La fecha desde no puede superar la fecha hasta.");
        if(ChronoUnit.DAYS.between(inicio,fin)>365) throw new DatosInvalidosException("Consultá como máximo 366 días por reporte.");
        return new Periodo(inicio,fin);
    }
    public Resumen resumen(LocalDate desde,LocalDate hasta) {
        var periodo=periodo(desde,hasta); long taller=sesion.tallerId(); var cobros=reportes.cobros(taller,periodo); var hoy=hoy();
        return new Resumen(hoy,periodo,reportes.operacion(taller,hoy),reportes.pendientes(taller),cobros.cantidad(),cobros.importe(),reportes.stockBajo(taller));
    }
    public Ingresos ingresos(LocalDate desde,LocalDate hasta) {
        var periodo=periodo(desde,hasta); long taller=sesion.tallerId(); var cobros=reportes.cobros(taller,periodo);
        var promedio=cobros.cantidad()==0?BigDecimal.ZERO.setScale(2):cobros.importe().divide(BigDecimal.valueOf(cobros.cantidad()),2,RoundingMode.HALF_UP);
        return new Ingresos(periodo,cobros.cantidad(),cobros.importe(),promedio,reportes.ingresosDiarios(taller,periodo),reportes.ingresosPorMedio(taller,periodo));
    }
    public Trabajos trabajos(LocalDate desde,LocalDate hasta) {
        var periodo=periodo(desde,hasta); long taller=sesion.tallerId(); var creadas=reportes.ordenesCreadas(taller,periodo);
        return new Trabajos(periodo,creadas.stream().mapToLong(Cantidad::cantidad).sum(),reportes.finalizadas(taller,periodo),creadas,
                reportes.finalizadasPorTipo(taller,periodo),reportes.finalizadasPorMecanico(taller,periodo));
    }
    public Repuestos repuestos(LocalDate desde,LocalDate hasta,int limite) {
        var periodo=periodo(desde,hasta); long taller=sesion.tallerId();
        return new Repuestos(periodo,limite,reportes.movimientos(taller,periodo,limite),reportes.stockBajo(taller),reportes.alertas(taller,limite));
    }
}
