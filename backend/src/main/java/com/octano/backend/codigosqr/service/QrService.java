package com.octano.backend.codigosqr.service;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.codigosqr.dto.QrDatos.*;
import com.octano.backend.codigosqr.repository.QrRepository;
import com.octano.backend.codigosqr.repository.QrRepository.Registro;
import com.octano.backend.compartido.exception.*;
import com.octano.backend.integraciones.qr.GeneradorQr;
import com.octano.backend.talleres.repository.TallerRepository;
import com.octano.backend.vehiculos.repository.VehiculoRepository;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class QrService {
    private final QrRepository codigos;
    private final VehiculoRepository vehiculos;
    private final TallerRepository talleres;
    private final SesionActual sesion;
    private final GeneradorQr generador;
    private final String frontendUrl;

    public QrService(QrRepository codigos,VehiculoRepository vehiculos,TallerRepository talleres,SesionActual sesion,
                     GeneradorQr generador,@Value("${octano.qr.frontend-url}") String frontendUrl) {
        this.codigos=codigos; this.vehiculos=vehiculos; this.talleres=talleres; this.sesion=sesion; this.generador=generador;
        var uri=URI.create(frontendUrl);
        if(frontendUrl.length()>2000 || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost()==null || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null) {
            throw new IllegalStateException("PUBLIC_FRONTEND_URL debe ser una URL HTTP o HTTPS, sin credenciales, query ni fragmento.");
        }
        this.frontendUrl=frontendUrl.replaceAll("/+$","");
    }
    private String codigo(String valor) {
        String codigo=valor.strip().toUpperCase(Locale.ROOT);
        if(!codigo.matches("[A-Z0-9-]{3,40}")) throw new DatosInvalidosException("El código QR debe tener entre 3 y 40 letras, números o guiones.");
        return codigo;
    }
    private Estado estado(Registro r) {
        return r.inutilizadoEn()!=null?Estado.INUTILIZADO:r.vehiculoId()==null?Estado.DISPONIBLE:Estado.ASIGNADO;
    }
    private Codigo respuesta(Registro r) {
        return new Codigo(r.codigo(),estado(r),r.vehiculoId(),r.patente(),r.marca(),r.modelo(),
                r.creadoEn(),r.asignadoEn(),r.impresoEn(),r.impresoPor(),r.fechaModif(),r.usuarioModificacionId(),frontendUrl+"/qr/"+r.codigo());
    }
    private Registro buscar(String codigo,boolean bloquear) {
        return codigos.buscar(codigo(codigo),sesion.tallerId(),bloquear).orElseThrow(()->new RecursoNoEncontradoException("No existe el código QR solicitado."));
    }
    private void bloquearTaller() { talleres.findLockedById(sesion.tallerId()).orElseThrow(); }
    public Pagina listar(String q,Estado estado,Boolean impreso,Long vehiculo,int pagina,int tamano) {
        long taller=sesion.tallerId();
        long total=codigos.contar(taller,q.strip(),estado,impreso,vehiculo);
        return new Pagina(codigos.listar(taller,q.strip(),estado,impreso,vehiculo,pagina,tamano).stream().map(this::respuesta).toList(),
                pagina,tamano,total,(total+tamano-1)/tamano);
    }
    public Codigo obtener(String codigo) { return respuesta(buscar(codigo,false)); }
    @Transactional public List<Codigo> generarLote() {
        bloquearTaller();
        var lote=new ArrayList<Codigo>();
        for(int i=0;i<16;i++) {
            // Aleatorio y no correlativo: conocer una etiqueta no permite adivinar las demás.
            String codigo="OCT-"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);
            codigos.crear(codigo,sesion.tallerId(),sesion.usuarioId());
            lote.add(obtener(codigo));
        }
        return lote;
    }
    @Transactional public Codigo asignar(String codigo,long vehiculo) {
        bloquearTaller();
        var qr=buscar(codigo,true);
        if(estado(qr)!=Estado.DISPONIBLE) throw new DatosInvalidosException("Ese QR ya fue utilizado. Elegí un código disponible.");
        var v=vehiculos.buscar(vehiculo,sesion.tallerId(),true).orElseThrow(()->new RecursoNoEncontradoException("No existe el vehículo solicitado."));
        if(!v.getActivo()) throw new DatosInvalidosException("Seleccioná un vehículo activo.");
        // El QR anterior queda inutilizado para siempre, conservando su registro de impresión.
        codigos.liberarVehiculo(vehiculo,sesion.tallerId(),sesion.usuarioId());
        codigos.asignar(qr.codigo(),vehiculo,sesion.tallerId(),sesion.usuarioId());
        auditarVehiculo(vehiculo);
        return obtener(qr.codigo());
    }
    @Transactional public Codigo desvincular(String codigo) {
        bloquearTaller(); var qr=buscar(codigo,true);
        if(qr.vehiculoId()==null) throw new DatosInvalidosException("El QR no tiene un vehículo asignado.");
        codigos.liberarVehiculo(qr.vehiculoId(),sesion.tallerId(),sesion.usuarioId());
        auditarVehiculo(qr.vehiculoId());
        return obtener(qr.codigo());
    }
    private void auditarVehiculo(long id) {
        var v=vehiculos.buscar(id,sesion.tallerId(),true).orElseThrow();
        v.setFechaModif(Instant.now()); v.setUsuarioModificacionId(sesion.usuarioId()); vehiculos.save(v);
    }
    @Transactional public List<Codigo> imprimir(List<String> seleccion) {
        bloquearTaller();
        validarSeleccion(seleccion);
        codigos.imprimir(seleccion,sesion.tallerId(),sesion.usuarioId());
        return seleccion.stream().map(this::obtener).toList();
    }
    @Transactional public List<Codigo> plantilla(List<String> seleccion) {
        bloquearTaller();
        validarSeleccion(seleccion);
        return seleccion.stream().map(this::obtener).toList();
    }
    private void validarSeleccion(List<String> seleccion) {
        if(seleccion.size()!=16 || seleccion.stream().distinct().count()!=16) throw new DatosInvalidosException("Seleccioná exactamente 16 códigos distintos sin imprimir.");
        for(String codigo:seleccion.stream().sorted().toList()) {
            var qr=buscar(codigo,true);
            if(estado(qr)==Estado.INUTILIZADO) throw new DatosInvalidosException("No se pueden imprimir códigos inutilizados.");
            if(qr.impresoEn()!=null) throw new DatosInvalidosException("Uno de los códigos ya fue marcado como impreso.");
        }
    }
    public byte[] imagen(String codigo,int tamano) { return generador.png(obtener(codigo).urlPublica(),tamano); }
    public Ficha ficha(String valor) {
        var qr=codigos.publico(codigo(valor)).orElseThrow(()->new RecursoNoEncontradoException("Código QR no registrado."));
        String tallerNombre=talleres.findById(qr.tallerId()).orElseThrow().getNombre();
        if(qr.vehiculoId()==null) return new Ficha(qr.codigo(),estado(qr),tallerNombre,null,null,null);
        var v=vehiculos.buscar(qr.vehiculoId(),qr.tallerId(),false).orElseThrow(()->new RecursoNoEncontradoException("Vehículo no encontrado."));
        var publico=new VehiculoPublico(v.getPatente(),v.getMarca(),v.getModelo(),v.getVersion(),v.getAnio(),v.getKilometraje());
        return new Ficha(qr.codigo(),Estado.ASIGNADO,tallerNombre,publico,codigos.ultimoMantenimiento(v.getId(),qr.tallerId(),"SERVICE").orElse(null),
                codigos.ultimoMantenimiento(v.getId(),qr.tallerId(),"DISTRIBUCION").orElse(null));
    }
}
