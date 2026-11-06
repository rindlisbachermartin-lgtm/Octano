package com.octano.backend.ordenes.service;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.clientes.repository.ClienteRepository;
import com.octano.backend.compartido.exception.*;
import com.octano.backend.mecanicos.repository.MecanicoRepository;
import com.octano.backend.ordenes.dto.OrdenesDatos.*;
import com.octano.backend.ordenes.entity.OrdenTrabajo;
import com.octano.backend.ordenes.entity.OrdenTrabajo.Estado;
import com.octano.backend.ordenes.repository.OrdenTrabajoRepository;
import com.octano.backend.presupuestos.entity.Presupuesto;
import com.octano.backend.presupuestos.repository.PresupuestoRepository;
import com.octano.backend.repuestos.repository.RepuestoRepository;
import com.octano.backend.stock.dto.StockDatos;
import com.octano.backend.stock.service.StockService;
import com.octano.backend.talleres.repository.TallerRepository;
import com.octano.backend.turnos.entity.Turno;
import com.octano.backend.turnos.repository.TurnoRepository;
import com.octano.backend.usuarios.entity.Rol;
import com.octano.backend.vehiculos.repository.VehiculoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class OrdenTrabajoService {
    private final OrdenTrabajoRepository ordenes;
    private final TallerRepository talleres;
    private final VehiculoRepository vehiculos;
    private final ClienteRepository clientes;
    private final MecanicoRepository mecanicos;
    private final PresupuestoRepository presupuestos;
    private final TurnoRepository turnos;
    private final RepuestoRepository repuestos;
    private final StockService stock;
    private final SesionActual sesion;
    public OrdenTrabajoService(OrdenTrabajoRepository ordenes,TallerRepository talleres,VehiculoRepository vehiculos,
            ClienteRepository clientes,MecanicoRepository mecanicos,PresupuestoRepository presupuestos,TurnoRepository turnos,
            RepuestoRepository repuestos,StockService stock,SesionActual sesion) {
        this.ordenes=ordenes;this.talleres=talleres;this.vehiculos=vehiculos;this.clientes=clientes;this.mecanicos=mecanicos;
        this.presupuestos=presupuestos;this.turnos=turnos;this.repuestos=repuestos;this.stock=stock;this.sesion=sesion;
    }
    private boolean esMecanico() { return sesion.usuario().getTipoUsuarioId()==Rol.MECANICO.id(); }
    private void bloquearTaller() { talleres.findLockedById(sesion.tallerId()).orElseThrow(); }
    public boolean presupuestoOcupado(long id) { return ordenes.presupuestoUtilizado(id,sesion.tallerId(),null); }
    public boolean presupuestoEnOrdenIngresada(long id) { return ordenes.presupuestoIngresado(id,sesion.tallerId()); }
    @Transactional public void cancelarPorPresupuesto(long id) {
        ordenes.cancelarPorPresupuesto(id,sesion.tallerId(),sesion.usuarioId());
    }
    private OrdenTrabajo buscar(long id,boolean bloquear) {
        var o=ordenes.buscar(id,sesion.tallerId(),bloquear).orElseThrow(()->new RecursoNoEncontradoException("No existe la orden solicitada."));
        if(esMecanico() && !Objects.equals(o.mecanicoId(),sesion.usuario().getMecanicoId()))
            throw new RecursoNoEncontradoException("No existe la orden solicitada.");
        return o;
    }
    private void validarMecanico(long id) {
        var m=mecanicos.findLockedByIdAndTallerId(id,sesion.tallerId()).orElseThrow(()->new RecursoNoEncontradoException("No existe el mecánico solicitado."));
        if(!m.getActivo()) throw new DatosInvalidosException("Seleccioná un mecánico activo.");
    }
    public Pagina listar(String q,Estado estado,Long cliente,Long vehiculo,Long mecanico,LocalDate desde,LocalDate hasta,int pagina,int tamano) {
        if(desde!=null && hasta!=null && desde.isAfter(hasta)) throw new DatosInvalidosException("La fecha desde no puede superar la fecha hasta.");
        if(esMecanico()) mecanico=sesion.usuario().getMecanicoId();
        long taller=sesion.tallerId();
        long total=ordenes.contar(taller,q.strip(),estado,cliente,vehiculo,mecanico,desde,hasta);
        return new Pagina(ordenes.listar(taller,q.strip(),estado,cliente,vehiculo,mecanico,desde,hasta,pagina,tamano).stream().map(Cabecera::desde).toList(),pagina,tamano,total,(total+tamano-1)/tamano);
    }
    public Respuesta obtener(long id) {
        var o=buscar(id,false); var tareas=ordenes.tareas(id,o.tallerId()); var items=ordenes.items(id,o.tallerId());
        var rep=importe(items.stream().map(Item::subtotal).reduce(BigDecimal.ZERO,BigDecimal::add));
        var subtotal=importe(rep.add(o.manoObra())); var iva=importe(subtotal.multiply(o.porcentajeIva().movePointLeft(2)));
        int progreso=tareas.isEmpty()?0:(int)(tareas.stream().filter(Tarea::completada).count()*100/tareas.size());
        return new Respuesta(Cabecera.desde(o),tareas,items,ordenes.fotos(id,o.tallerId()),progreso,rep,subtotal,iva,importe(subtotal.add(iva)));
    }
    @Transactional public Respuesta guardar(Long id,Solicitud s) {
        bloquearTaller();
        var actual=id==null?null:buscar(id,true);
        if(actual!=null && !actual.sinIniciar()) throw new DatosInvalidosException("Los datos de planificación solo pueden editarse antes de iniciar la orden.");
        if(actual!=null && (!actual.vehiculoId().equals(s.vehiculoId()) || !Objects.equals(actual.presupuestoId(),s.presupuestoId())))
            throw new DatosInvalidosException("El vehículo y presupuesto de una orden emitida no se pueden reemplazar. Cancelá la orden si necesitás corregirlos.");
        validarMecanico(s.mecanicoId());
        var v=vehiculos.buscar(s.vehiculoId(),sesion.tallerId(),true).orElseThrow(()->new RecursoNoEncontradoException("No existe el vehículo solicitado."));
        long cliente=actual==null?v.getClienteId():actual.clienteId();
        var c=clientes.findByIdAndTallerId(cliente,sesion.tallerId(),true).orElseThrow();
        if(!v.getActivo() || !c.getActivo()) throw new DatosInvalidosException("Seleccioná un cliente y vehículo activos.");
        var manoObra=s.manoObra(); var iva=s.porcentajeIva();
        if(s.presupuestoId()!=null) {
            var p=presupuestos.buscar(s.presupuestoId(),sesion.tallerId(),true).orElseThrow(()->new RecursoNoEncontradoException("No existe el presupuesto solicitado."));
            if(p.estado()==Presupuesto.Estado.ANULADO || p.estado()==Presupuesto.Estado.RECHAZADO || !p.vehiculoId().equals(v.getId()) || !p.clienteId().equals(cliente))
                throw new DatosInvalidosException("El presupuesto no está disponible para este cliente y vehículo.");
            if(ordenes.presupuestoUtilizado(p.id(),sesion.tallerId(),id)) throw new DatosInvalidosException("El presupuesto ya tiene una orden activa.");
            if(id==null && presupuestos.turnoActivo(p.id(),sesion.tallerId(),null).isPresent()) throw new DatosInvalidosException("El presupuesto tiene un turno: utilizá la orden de ese turno.");
            manoObra=p.manoObra(); iva=p.porcentajeIva();
            var tipos=java.util.stream.Stream.concat(p.tiposTrabajo().stream(),s.tiposTrabajo().stream()).distinct().toList();
            s=new Solicitud(s.vehiculoId(),s.presupuestoId(),s.mecanicoId(),s.descripcion(),tipos,s.indicaciones(),s.manoObra(),s.porcentajeIva());
        }
        long orden=ordenes.guardar(id,sesion.tallerId(),sesion.usuarioId(),cliente,s,manoObra,iva);
        if(id==null) { ordenes.copiarPresupuesto(orden,sesion.tallerId(),s.presupuestoId()); ordenes.tarea(orden,sesion.tallerId(),s.descripcion().substring(0,Math.min(500,s.descripcion().length()))); }
        return obtener(orden);
    }
    // Se ejecuta dentro de la misma transacción que el turno: ambos quedan guardados o ninguno.
    @Transactional public void sincronizarTurno(Turno t) {
        var existente=ordenes.porTurno(t.id(),t.tallerId());
        if(existente.isEmpty()) {
            if(t.estado()==Turno.Estado.CANCELADO || t.estado()==Turno.Estado.NO_ASISTIO) return;
            long id=ordenes.crearDesdeTurno(t,sesion.usuarioId());
            ordenes.copiarPresupuesto(id,t.tallerId(),t.presupuestoId());
            ordenes.tarea(id,t.tallerId(),t.motivoVisita());
            return;
        }
        var o=ordenes.buscar(existente.get(),t.tallerId(),true).orElseThrow();
        if(o.estado()!=Estado.PENDIENTE_INGRESO) return;
        if(t.estado()==Turno.Estado.CANCELADO || t.estado()==Turno.Estado.NO_ASISTIO) {
            ordenes.estado(o.id(),o.tallerId(),sesion.usuarioId(),Estado.CANCELADO,t.estado()==Turno.Estado.NO_ASISTIO?"El vehículo no asistió al turno.":"Turno cancelado.");
        } else {
            ordenes.sincronizar(o.id(),t,sesion.usuarioId());
            if(!Objects.equals(o.presupuestoId(),t.presupuestoId())) ordenes.copiarPresupuesto(o.id(),t.tallerId(),t.presupuestoId());
            if(t.estado()==Turno.Estado.EN_TALLER) ordenes.estado(o.id(),o.tallerId(),sesion.usuarioId(),Estado.EN_ESPERA,null);
        }
    }
    @Transactional public Respuesta ingresar(long id) {
        bloquearTaller(); var o=buscar(id,true);
        if(o.estado()==Estado.EN_ESPERA) return obtener(id);
        if(o.estado()!=Estado.PENDIENTE_INGRESO || o.turnoId()==null) throw new DatosInvalidosException("La orden no está pendiente de ingreso.");
        var t=turnos.buscar(o.turnoId(),o.tallerId(),true).orElseThrow();
        if(!t.programado()) throw new DatosInvalidosException("El turno no está disponible para ingresar.");
        turnos.estado(t.id(),o.tallerId(),sesion.usuarioId(),Turno.Estado.EN_TALLER);
        ordenes.estado(id,o.tallerId(),sesion.usuarioId(),Estado.EN_ESPERA,null);
        return obtener(id);
    }
    @Transactional public Respuesta iniciar(long id) {
        bloquearTaller(); var o=buscar(id,true);
        if(o.estado()==Estado.EN_PROCESO) return obtener(id);
        if(o.estado()!=Estado.EN_ESPERA || o.mecanicoId()==null) throw new DatosInvalidosException("La orden debe estar en espera y tener un mecánico asignado.");
        validarMecanico(o.mecanicoId());
        var v=vehiculos.buscar(o.vehiculoId(),o.tallerId(),true).orElseThrow();
        var c=clientes.findByIdAndTallerId(o.clienteId(),o.tallerId(),true).orElseThrow();
        if(!v.getActivo() || !c.getActivo()) throw new DatosInvalidosException("El cliente y el vehículo deben estar activos.");
        consumir(o);
        ordenes.estado(id,o.tallerId(),sesion.usuarioId(),Estado.EN_PROCESO,null);
        return obtener(id);
    }
    private void editable(OrdenTrabajo o) {
        if(o.estado()==Estado.CANCELADO || o.estado()==Estado.FINALIZADO) throw new DatosInvalidosException("La orden está cerrada y no se puede modificar.");
    }
    @Transactional public Respuesta trabajo(long id,Trabajo s) {
        bloquearTaller(); var o=buscar(id,true); editable(o);
        if(o.estado()!=Estado.EN_PROCESO) throw new DatosInvalidosException("Iniciá la orden para registrar el trabajo realizado.");
        ordenes.trabajo(id,o.tallerId(),s); ordenes.tocar(id,o.tallerId(),sesion.usuarioId());
        return obtener(id);
    }
    @Transactional public Respuesta finalizar(long id,Cierre s) {
        bloquearTaller(); var o=buscar(id,true);
        if(o.estado()!=Estado.EN_PROCESO) throw new DatosInvalidosException("Solo se puede finalizar una orden en proceso.");
        boolean service=o.tiposTrabajo().contains(Presupuesto.TipoTrabajo.SERVICE);
        if(service && s.aceite().isBlank()) throw new DatosInvalidosException("Indicá el aceite utilizado en el service.");
        if(!service && (!s.aceite().isEmpty() || !s.filtros().isEmpty())) throw new DatosInvalidosException("Aceite y filtros corresponden a un trabajo de service.");
        var v=vehiculos.buscar(o.vehiculoId(),o.tallerId(),true).orElseThrow();
        // El cierre y el kilometraje del vehículo se confirman juntos.
        v.setKilometraje(s.kilometraje());v.setFechaModif(Instant.now());v.setUsuarioModificacionId(sesion.usuarioId());vehiculos.save(v);
        ordenes.cierre(id,o.tallerId(),s);ordenes.estado(id,o.tallerId(),sesion.usuarioId(),Estado.FINALIZADO,null);
        if(o.turnoId()!=null) turnos.estado(o.turnoId(),o.tallerId(),sesion.usuarioId(),Turno.Estado.FINALIZADO);
        return obtener(id);
    }
    @Transactional public Respuesta cancelar(long id,String motivo) {
        bloquearTaller(); var o=buscar(id,true);
        if(o.estado()==Estado.CANCELADO) return obtener(id);
        if(o.estado()==Estado.FINALIZADO) throw new DatosInvalidosException("Una orden finalizada no se puede cancelar.");
        // Los materiales ya utilizados no vuelven al inventario por cancelar el trabajo.
        if(o.turnoId()!=null) turnos.estado(o.turnoId(),o.tallerId(),sesion.usuarioId(),Turno.Estado.CANCELADO);
        ordenes.estado(id,o.tallerId(),sesion.usuarioId(),Estado.CANCELADO,motivo);
        return obtener(id);
    }
    @Transactional public Respuesta agregarTarea(long id,String descripcion) {
        bloquearTaller();var o=buscar(id,true);editable(o);
        if(ordenes.tareas(id,o.tallerId()).size()>=200) throw new DatosInvalidosException("La orden admite hasta 200 tareas.");
        ordenes.tarea(id,o.tallerId(),descripcion);ordenes.tocar(id,o.tallerId(),sesion.usuarioId());return obtener(id);
    }
    @Transactional public Respuesta tareaEstado(long id,long tarea,boolean completada) {
        bloquearTaller();var o=buscar(id,true);editable(o);
        if(o.estado()!=Estado.EN_PROCESO) throw new DatosInvalidosException("Iniciá la orden antes de completar tareas.");
        if(!ordenes.tareaEstado(id,o.tallerId(),tarea,completada)) throw new RecursoNoEncontradoException("No existe la tarea solicitada.");
        ordenes.tocar(id,o.tallerId(),sesion.usuarioId());return obtener(id);
    }
    @Transactional public Respuesta quitarTarea(long id,long tarea) {
        bloquearTaller();var o=buscar(id,true);editable(o);
        if(!ordenes.quitarTarea(id,o.tallerId(),tarea)) throw new RecursoNoEncontradoException("No existe la tarea solicitada.");
        ordenes.tocar(id,o.tallerId(),sesion.usuarioId());return obtener(id);
    }
    @Transactional public Respuesta agregarItems(long id,List<ItemSolicitud> items) {
        bloquearTaller();var o=buscar(id,true);editable(o);
        if(ordenes.items(id,o.tallerId()).size()+items.size()>200) throw new DatosInvalidosException("La orden admite hasta 200 renglones de repuestos.");
        // Mismo orden de bloqueo al consumir varios repuestos para evitar interbloqueos.
        for(var s:items.stream().sorted(Comparator.comparing(ItemSolicitud::repuestoId,Comparator.nullsLast(Comparator.naturalOrder()))).toList()) {
            String codigo=null,descripcion=s.descripcion();var precio=s.precioUnitario();
            if(esMecanico() && (s.repuestoId()==null || precio!=null)) throw new AccessDeniedException("El mecánico solo agrega repuestos de catálogo con su precio vigente.");
            if(s.repuestoId()!=null) {
                var r=repuestos.buscar(s.repuestoId(),o.tallerId(),true).orElseThrow(()->new RecursoNoEncontradoException("No existe uno de los repuestos solicitados."));
                if(!r.activo()) throw new DatosInvalidosException("No se puede agregar un repuesto inactivo.");
                validarCompatibilidad(o,s.repuestoId());
                codigo=r.codigoOem();descripcion=r.descripcion();if(precio==null) precio=r.precioPublico();
            } else if(descripcion.isBlank() || precio==null) throw new DatosInvalidosException("El repuesto manual necesita descripción y precio.");
            ordenes.item(id,o.tallerId(),s,codigo,descripcion,importe(precio),importe(precio.multiply(s.cantidad())));
        }
        if(o.estado()==Estado.EN_PROCESO) consumir(o);
        ordenes.tocar(id,o.tallerId(),sesion.usuarioId());return obtener(id);
    }
    private void validarCompatibilidad(OrdenTrabajo o,long repuesto) {
        var opciones=repuestos.compatibilidades(repuesto,o.tallerId());
        if(opciones.isEmpty()) return; // Sin restricciones cargadas: repuesto de uso general.
        var v=vehiculos.buscar(o.vehiculoId(),o.tallerId(),false).orElseThrow();
        if(opciones.stream().noneMatch(c->c.modeloId().equals(v.getModeloId()) && (c.versionId()==null || c.versionId().equals(v.getVersionId()))))
            throw new DatosInvalidosException("El repuesto no es compatible con el vehículo de la orden.");
    }
    private void consumir(OrdenTrabajo o) {
        for(var i:ordenes.items(o.id(),o.tallerId()).stream().filter(i->!i.consumido())
                .sorted(Comparator.comparing(Item::repuestoId,Comparator.nullsLast(Comparator.naturalOrder()))).toList()) {
            if(i.repuestoId()!=null) {
                validarCompatibilidad(o,i.repuestoId());
                stock.mover(i.repuestoId(),new StockDatos.Solicitud(StockDatos.Tipo.SALIDA,i.cantidad(),"Consumo OT #"+o.id()+", renglón #"+i.id()));
            }
            ordenes.consumido(o.id(),o.tallerId(),i.id());
        }
    }
    @Transactional public Respuesta quitarItem(long id,long item) {
        bloquearTaller();var o=buscar(id,true);editable(o);
        var i=ordenes.items(id,o.tallerId()).stream().filter(r->r.id()==item).findFirst().orElseThrow(()->new RecursoNoEncontradoException("No existe el renglón solicitado."));
        if(i.consumido() && i.repuestoId()!=null) stock.devolverDeOrden(i.repuestoId(),i.cantidad(),"Devolución OT #"+id+", renglón #"+item);
        ordenes.quitarItem(id,o.tallerId(),item);ordenes.tocar(id,o.tallerId(),sesion.usuarioId());return obtener(id);
    }
    private BigDecimal importe(BigDecimal valor) {
        var r=valor.setScale(2,RoundingMode.HALF_UP);
        if(r.compareTo(new BigDecimal("9999999999999999.99"))>0) throw new DatosInvalidosException("El importe supera el máximo admitido.");
        return r;
    }
    @Transactional public Respuesta agregarFoto(long id,FotoSolicitud s) {
        bloquearTaller();var o=buscar(id,true);validarFotosEditables(o);
        if(ordenes.fotos(id,o.tallerId()).size()>=8) throw new DatosInvalidosException("La orden admite hasta 8 fotos.");
        String tipo=s.datos().startsWith("data:image/jpeg;base64,")?"image/jpeg":
                s.datos().startsWith("data:image/png;base64,")?"image/png":null;
        if(tipo==null) throw new DatosInvalidosException("Adjuntá una imagen JPEG o PNG en formato data URL.");
        byte[] datos;
        try { datos=Base64.getDecoder().decode(s.datos().substring(s.datos().indexOf(',')+1)); }
        catch(IllegalArgumentException e) { throw new DatosInvalidosException("La imagen no tiene un base64 válido."); }
        if(datos.length==0 || datos.length>1048576) throw new DatosInvalidosException("Cada foto debe pesar como máximo 1 MiB.");
        validarImagen(datos,tipo);
        ordenes.foto(id,o.tallerId(),sesion.usuarioId(),s.sector(),tipo,datos);ordenes.tocar(id,o.tallerId(),sesion.usuarioId());
        return obtener(id);
    }
    private void validarImagen(byte[] datos,String tipo) {
        // Leemos el tamaño antes de descomprimir para no aceptar imágenes con dimensiones enormes.
        try(var entrada=new MemoryCacheImageInputStream(new ByteArrayInputStream(datos))) {
            var lectores=ImageIO.getImageReaders(entrada);
            if(!lectores.hasNext()) throw new DatosInvalidosException("El archivo no es una imagen válida.");
            var lector=lectores.next();
            try {
                lector.setInput(entrada,true,true);
                String formato=lector.getFormatName();
                if(!(tipo.equals("image/jpeg") && formato.equalsIgnoreCase("JPEG")) && !(tipo.equals("image/png") && formato.equalsIgnoreCase("PNG")))
                    throw new DatosInvalidosException("El tipo de imagen no coincide con el archivo.");
                if((long)lector.getWidth(0)*lector.getHeight(0)>16000000) throw new DatosInvalidosException("La foto admite hasta 16 megapíxeles.");
                lector.read(0);
            } finally { lector.dispose(); }
        } catch(IOException | IllegalArgumentException e) { throw new DatosInvalidosException("No se pudo leer la imagen adjunta."); }
    }
    private void validarFotosEditables(OrdenTrabajo o) {
        if(o.estado()!=Estado.EN_ESPERA && o.estado()!=Estado.EN_PROCESO) throw new DatosInvalidosException("Las fotos se cargan mientras el vehículo está en espera o en proceso.");
    }
    public FotoContenido foto(long id,long foto) {
        var o=buscar(id,false);
        return ordenes.foto(id,o.tallerId(),foto).orElseThrow(()->new RecursoNoEncontradoException("No existe la foto solicitada."));
    }
    @Transactional public Respuesta quitarFoto(long id,long foto) {
        bloquearTaller();var o=buscar(id,true);validarFotosEditables(o);
        if(!ordenes.quitarFoto(id,o.tallerId(),foto)) throw new RecursoNoEncontradoException("No existe la foto solicitada.");
        ordenes.tocar(id,o.tallerId(),sesion.usuarioId());return obtener(id);
    }
}
