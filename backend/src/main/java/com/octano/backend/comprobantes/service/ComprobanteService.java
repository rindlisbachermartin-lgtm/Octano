package com.octano.backend.comprobantes.service;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.clientes.repository.ClienteRepository;
import com.octano.backend.compartido.exception.*;
import com.octano.backend.comprobantes.dto.ComprobantesDatos.*;
import com.octano.backend.comprobantes.entity.Comprobante;
import com.octano.backend.comprobantes.entity.Comprobante.*;
import com.octano.backend.comprobantes.repository.ComprobanteRepository;
import com.octano.backend.condicionesiva.repository.CondicionIvaRepository;
import com.octano.backend.ordenes.entity.OrdenTrabajo;
import com.octano.backend.ordenes.repository.OrdenTrabajoRepository;
import com.octano.backend.repuestos.repository.RepuestoRepository;
import com.octano.backend.talleres.repository.TallerRepository;
import com.octano.backend.vehiculos.repository.VehiculoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class ComprobanteService {
    private static final BigDecimal MAXIMO=new BigDecimal("9999999999999999.99");
    private final ComprobanteRepository comprobantes;
    private final OrdenTrabajoRepository ordenes;
    private final TallerRepository talleres;
    private final ClienteRepository clientes;
    private final VehiculoRepository vehiculos;
    private final CondicionIvaRepository condiciones;
    private final RepuestoRepository repuestos;
    private final SesionActual sesion;
    public ComprobanteService(ComprobanteRepository comprobantes,OrdenTrabajoRepository ordenes,TallerRepository talleres,
            ClienteRepository clientes,VehiculoRepository vehiculos,CondicionIvaRepository condiciones,RepuestoRepository repuestos,SesionActual sesion) {
        this.comprobantes=comprobantes; this.ordenes=ordenes; this.talleres=talleres; this.clientes=clientes;
        this.vehiculos=vehiculos; this.condiciones=condiciones; this.repuestos=repuestos; this.sesion=sesion;
    }
    private void bloquearTaller() { talleres.findLockedById(sesion.tallerId()).orElseThrow(); }
    private Comprobante buscar(long id,boolean bloquear) {
        return comprobantes.buscar(id,sesion.tallerId(),bloquear).orElseThrow(()->new RecursoNoEncontradoException("No existe el comprobante solicitado."));
    }
    public Respuesta obtener(long id) {
        var c=buscar(id,false);
        return Respuesta.desde(c,comprobantes.items(id,c.tallerId()),comprobantes.cobro(id,c.tallerId()).orElse(null));
    }
    private void validarFechas(LocalDate desde,LocalDate hasta) {
        if(desde!=null && hasta!=null && desde.isAfter(hasta)) throw new DatosInvalidosException("La fecha desde no puede superar la fecha hasta.");
    }
    public Pagina listar(String q,Estado estado,Long cliente,Long vehiculo,Long orden,LocalDate desde,LocalDate hasta,int pagina,int tamano) {
        validarFechas(desde,hasta); long taller=sesion.tallerId();
        long total=comprobantes.contar(taller,q.strip(),estado,cliente,vehiculo,orden,desde,hasta);
        return new Pagina(comprobantes.listar(taller,q.strip(),estado,cliente,vehiculo,orden,desde,hasta,pagina,tamano).stream().map(Resumen::desde).toList(),
                pagina,tamano,total,(total+tamano-1)/tamano);
    }
    public PaginaPendientes pendientes(String q,LocalDate desde,LocalDate hasta,Boolean presupuesto,int pagina,int tamano) {
        validarFechas(desde,hasta); long taller=sesion.tallerId();
        long total=comprobantes.contarPendientes(taller,q.strip(),desde,hasta,presupuesto);
        return new PaginaPendientes(comprobantes.pendientes(taller,q.strip(),desde,hasta,presupuesto,pagina,tamano),pagina,tamano,total,(total+tamano-1)/tamano);
    }
    public PaginaCobros cobros(LocalDate desde,LocalDate hasta,MedioPago medio,int pagina,int tamano) {
        validarFechas(desde,hasta); long taller=sesion.tallerId();
        long total=comprobantes.contarCobros(taller,desde,hasta,medio);
        return new PaginaCobros(comprobantes.cobros(taller,desde,hasta,medio,pagina,tamano),pagina,tamano,total,(total+tamano-1)/tamano);
    }
    public Cobro obtenerCobro(long id) {
        buscar(id,false);
        return comprobantes.cobro(id,sesion.tallerId()).orElseThrow(()->new RecursoNoEncontradoException("El comprobante todavía no tiene un cobro."));
    }
    @Transactional public Respuesta guardar(Long id,Solicitud s) {
        bloquearTaller();
        var actual=id==null?null:buscar(id,true);
        if(actual!=null && actual.estado()!=Estado.BORRADOR) throw new DatosInvalidosException("Solo se puede editar un comprobante borrador.");
        if(actual!=null && !Objects.equals(actual.ordenId(),s.ordenId())) throw new DatosInvalidosException("No se puede cambiar la orden de un comprobante existente.");
        var v=vehiculos.buscar(s.vehiculoId(),sesion.tallerId(),true).orElseThrow(()->new RecursoNoEncontradoException("No existe el vehículo solicitado."));
        long cliente=v.getClienteId();
        if(s.ordenId()!=null) {
            var o=ordenFinalizada(s.ordenId());
            if(!o.vehiculoId().equals(s.vehiculoId())) throw new DatosInvalidosException("El vehículo no corresponde a la orden.");
            validarOrdenDisponible(s.ordenId(),id);
            cliente=o.clienteId();
        } else {
            var c=clientes.findByIdAndTallerId(cliente,sesion.tallerId(),true).orElseThrow();
            if(!v.getActivo() || !c.getActivo()) throw new DatosInvalidosException("Seleccioná un cliente y vehículo activos.");
        }
        var items=calcularItems(s.items());
        long comprobante=guardarBorrador(id,s.ordenId(),cliente,s.vehiculoId(),s.descripcion(),s.manoObra(),s.porcentajeIva(),
                actual==null?LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")):actual.fecha(),items);
        return obtener(comprobante);
    }
    private OrdenTrabajo ordenFinalizada(long id) {
        var o=ordenes.buscar(id,sesion.tallerId(),true).orElseThrow(()->new RecursoNoEncontradoException("No existe la orden solicitada."));
        if(o.estado()!=OrdenTrabajo.Estado.FINALIZADO) throw new DatosInvalidosException("El comprobante requiere una orden finalizada.");
        return o;
    }
    private void validarOrdenDisponible(long orden,Long excluir) {
        if(comprobantes.porOrden(orden,sesion.tallerId()).filter(id->!Objects.equals(id,excluir)).isPresent())
            throw new DatosInvalidosException("La orden ya tiene un comprobante. Consultá o editá su borrador.");
    }
    @Transactional public Respuesta desdeOrden(long id) {
        bloquearTaller(); return obtener(crearDesdeOrden(id));
    }
    private long crearDesdeOrden(long id) {
        var o=ordenFinalizada(id); validarOrdenDisponible(id,null);
        var items=new ArrayList<Item>();
        // La orden ya contiene los materiales presupuestados y los adicionales; se copian una sola vez.
        for(var i:ordenes.items(id,sesion.tallerId())) items.add(new Item(items.size()+1,i.repuestoId(),i.codigoOem(),i.descripcion(),i.cantidad(),i.precioUnitario(),i.subtotal()));
        return guardarBorrador(null,id,o.clienteId(),o.vehiculoId(),o.descripcion(),o.manoObra(),o.porcentajeIva(),
                LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")),items);
    }
    private List<Item> calcularItems(List<ItemSolicitud> solicitudes) {
        var items=new ArrayList<Item>();
        for(var s:solicitudes) {
            String codigo=null,descripcion=s.descripcion(); var precio=s.precioUnitario();
            if(s.repuestoId()!=null) {
                var r=repuestos.buscar(s.repuestoId(),sesion.tallerId(),true).orElseThrow(()->new RecursoNoEncontradoException("No existe uno de los repuestos solicitados."));
                if(!r.activo()) throw new DatosInvalidosException("No se puede seleccionar un repuesto inactivo.");
                codigo=r.codigoOem(); descripcion=r.descripcion(); if(precio==null) precio=r.precioPublico();
            } else if(descripcion.isBlank() || precio==null) throw new DatosInvalidosException("El renglón manual necesita descripción y precio.");
            precio=importe(precio);
            items.add(new Item(items.size()+1,s.repuestoId(),codigo,descripcion,s.cantidad(),precio,importe(precio.multiply(s.cantidad()))));
        }
        return items;
    }
    private record Datos(Parte emisor,Parte receptor,Vehiculo vehiculo) { }
    private Datos datos(long cliente,long vehiculo) {
        var t=talleres.findLockedById(sesion.tallerId()).orElseThrow();
        var c=clientes.findByIdAndTallerId(cliente,sesion.tallerId(),true).orElseThrow(()->new RecursoNoEncontradoException("No existe el cliente solicitado."));
        var v=vehiculos.buscar(vehiculo,sesion.tallerId(),true).orElseThrow(()->new RecursoNoEncontradoException("No existe el vehículo solicitado."));
        var condicion=condiciones.findById(c.getCondicionIvaId()).orElseThrow();
        return new Datos(new Parte(t.getNombre(),t.getCuit(),t.getCalle(),t.getAltura(),t.getCondicionIva().getNombre(),t.getTelefono()),
                new Parte(c.getNombre(),c.getDocumento(),c.getCalle(),c.getAltura(),condicion.getNombre(),c.getTelefono()),
                new Vehiculo(v.getPatente(),v.getMarca(),v.getModelo(),v.getVersion(),v.getAnio()));
    }
    private long guardarBorrador(Long id,Long orden,long cliente,long vehiculo,String descripcion,BigDecimal manoObra,BigDecimal porcentaje,
                                 LocalDate fecha,List<Item> items) {
        var materiales=importe(items.stream().map(Item::subtotal).reduce(BigDecimal.ZERO,BigDecimal::add));
        var subtotal=importe(materiales.add(manoObra));
        var iva=importe(subtotal.multiply(porcentaje.movePointLeft(2))); var total=importe(subtotal.add(iva));
        if(total.signum()<=0) throw new DatosInvalidosException("El comprobante debe tener un total mayor que cero. Completá los materiales o la mano de obra.");
        var datos=datos(cliente,vehiculo);
        var c=new Comprobante(id,sesion.tallerId(),orden,cliente,vehiculo,fecha,descripcion,Estado.BORRADOR,null,importe(manoObra),porcentaje,
                materiales,subtotal,iva,total,datos.emisor(),datos.receptor(),datos.vehiculo(),null,null,null,null,null,null);
        return comprobantes.guardar(c,sesion.usuarioId(),items);
    }
    private BigDecimal importe(BigDecimal valor) {
        var r=valor.setScale(2,RoundingMode.HALF_UP);
        if(r.compareTo(MAXIMO)>0) throw new DatosInvalidosException("El importe supera el máximo admitido.");
        return r;
    }
    @Transactional public Respuesta emitir(long id) {
        bloquearTaller(); var c=buscar(id,true);
        if(c.estado()==Estado.BORRADOR) emitirBorrador(c);
        return obtener(id);
    }
    private void emitirBorrador(Comprobante c) {
        var datos=datos(c.clienteId(),c.vehiculoId());
        comprobantes.emitir(c.id(),c.tallerId(),sesion.usuarioId(),datos.emisor(),datos.receptor(),datos.vehiculo());
    }
    @Transactional public Respuesta cobrar(long id,CobroSolicitud s) {
        bloquearTaller(); registrarCobro(id,s); return obtener(id);
    }
    @Transactional public Respuesta cobrarOrden(long id,CobroSolicitud s) {
        bloquearTaller();
        // Permite el botón «Registrar cobro» directo de una orden, sin crear dos comprobantes.
        ordenFinalizada(id);
        long comprobante=comprobantes.porOrden(id,sesion.tallerId()).orElseGet(()->crearDesdeOrden(id));
        registrarCobro(comprobante,s); return obtener(comprobante);
    }
    private void registrarCobro(long id,CobroSolicitud s) {
        var c=buscar(id,true);
        if(c.estado()==Estado.COBRADO) throw new DatosInvalidosException("El comprobante ya fue cobrado.");
        if(c.estado()==Estado.BORRADOR) emitirBorrador(c);
        comprobantes.cobrar(id,sesion.tallerId(),sesion.usuarioId(),s.medioPago(),s.observaciones());
    }
}
