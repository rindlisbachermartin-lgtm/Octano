package com.octano.backend.mecanicos.service;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.compartido.exception.RecursoNoEncontradoException;
import com.octano.backend.compartido.validation.CuitValidator;
import com.octano.backend.mecanicos.dto.MecanicoRespuesta;
import com.octano.backend.mecanicos.dto.MecanicoSolicitud;
import com.octano.backend.mecanicos.entity.Mecanico;
import com.octano.backend.mecanicos.repository.MecanicoRepository;
import com.octano.backend.talleres.repository.TallerRepository;
import com.octano.backend.usuarios.repository.UsuarioRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MecanicoService {
    private final MecanicoRepository mecanicos;
    private final UsuarioRepository usuarios;
    private final TallerRepository talleres;
    private final SesionActual sesion;

    public MecanicoService(MecanicoRepository mecanicos, UsuarioRepository usuarios,
                           TallerRepository talleres, SesionActual sesion) {
        this.mecanicos = mecanicos;
        this.usuarios = usuarios;
        this.talleres = talleres;
        this.sesion = sesion;
    }

    public List<MecanicoRespuesta> listar(Boolean activo) {
        long tallerId = sesion.tallerId();
        List<Mecanico> resultado = activo == null
                ? mecanicos.findByTallerIdOrderByApellidoAscNombreAsc(tallerId)
                : mecanicos.findByTallerIdAndActivoOrderByApellidoAscNombreAsc(tallerId, activo);
        return resultado.stream().map(MecanicoRespuesta::desde).toList();
    }

    public MecanicoRespuesta obtener(long id) {
        return MecanicoRespuesta.desde(buscar(id, false));
    }

    @Transactional
    public MecanicoRespuesta crear(MecanicoSolicitud solicitud) {
        Mecanico mecanico = new Mecanico();
        mecanico.setTallerId(sesion.tallerId());
        mecanico.setActivo(true);
        completar(mecanico, solicitud);
        return MecanicoRespuesta.desde(mecanicos.save(mecanico));
    }

    @Transactional
    public MecanicoRespuesta actualizar(long id, MecanicoSolicitud solicitud) {
        Mecanico mecanico = buscar(id, true);
        completar(mecanico, solicitud);
        return MecanicoRespuesta.desde(mecanicos.save(mecanico));
    }

    @Transactional
    public MecanicoRespuesta cambiarEstado(long id, boolean activo) {
        // Mismo orden de bloqueo que Usuarios: taller, mecánico y finalmente su cuenta.
        // Así, crear o reactivar una cuenta no se cruza con la baja del mecánico.
        talleres.findLockedById(sesion.tallerId()).orElseThrow();
        Mecanico mecanico = buscar(id, true);
        mecanico.setActivo(activo);
        if (!activo) {
            usuarios.desactivarPorMecanico(id, sesion.tallerId());
        }
        return MecanicoRespuesta.desde(mecanicos.save(mecanico));
    }

    private Mecanico buscar(long id, boolean bloquear) {
        long tallerId = sesion.tallerId();
        return (bloquear ? mecanicos.findLockedByIdAndTallerId(id, tallerId)
                : mecanicos.findByIdAndTallerId(id, tallerId))
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el mecánico solicitado."));
    }

    private void completar(Mecanico mecanico, MecanicoSolicitud solicitud) {
        if (solicitud.cuit() != null) CuitValidator.validar(solicitud.cuit());
        mecanico.setNombre(solicitud.nombre());
        mecanico.setApellido(solicitud.apellido());
        mecanico.setTelefono(solicitud.telefono());
        mecanico.setCalle(solicitud.calle());
        mecanico.setCuit(solicitud.cuit());
        mecanico.setFechaInicio(solicitud.fechaInicio());
    }
}
