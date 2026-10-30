package com.octano.backend.service;
import com.octano.backend.dto.UsuariosDatos;
import com.octano.backend.entity.*;
import com.octano.backend.exception.*;
import com.octano.backend.repository.*;
import com.octano.backend.security.SesionActual;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final MecanicoRepository mecanicos;
    private final TallerRepository talleres;
    private final SesionActual sesion;
    private final PasswordEncoder passwords;
    public UsuarioService(UsuarioRepository usuarios, MecanicoRepository mecanicos, TallerRepository talleres,
                          SesionActual sesion, PasswordEncoder passwords) {
        this.usuarios=usuarios; this.mecanicos=mecanicos; this.talleres=talleres; this.sesion=sesion; this.passwords=passwords;
    }
    public List<UsuariosDatos.Respuesta> listar() {
        return usuarios.findByTallerIdOrderById(sesion.tallerId()).stream().map(UsuariosDatos.Respuesta::desde).toList();
    }
    public UsuariosDatos.Respuesta obtener(long id) {
        return usuarios.findByIdAndTallerId(id,sesion.tallerId()).map(UsuariosDatos.Respuesta::desde)
            .orElseThrow(()->new RecursoNoEncontradoException("No existe el usuario solicitado."));
    }
    @Transactional public UsuariosDatos.Respuesta crear(UsuariosDatos.Alta solicitud) {
        AuthService.validarPassword(solicitud.password());
        Usuario usuario=new Usuario();
        usuario.setTallerId(sesion.tallerId());
        usuario.setEmail(AuthService.normalizarEmail(solicitud.email()));
        if(usuarios.findByEmail(usuario.getEmail()).isPresent()) throw new DatosInvalidosException("El correo ya está registrado.");
        if(solicitud.rol()==Rol.MECANICO) {
            if(solicitud.mecanicoId()==null) throw new DatosInvalidosException("Seleccioná el mecánico del usuario.");
            mecanicos.findLockedByIdAndTallerId(solicitud.mecanicoId(), sesion.tallerId()).filter(Mecanico::getActivo)
                .orElseThrow(()->new RecursoNoEncontradoException("No existe un mecánico activo con ese ID en tu taller."));
        } else if(solicitud.mecanicoId()!=null) throw new DatosInvalidosException("Solo el rol MECANICO puede vincularse a un mecánico.");
        usuario.setMecanicoId(solicitud.mecanicoId());
        usuario.setTipoUsuarioId(solicitud.rol().id()); usuario.setActivo(true);
        usuario.setPasswordHash(passwords.encode(solicitud.password()));
        return UsuariosDatos.Respuesta.desde(usuarios.save(usuario));
    }
    @Transactional public UsuariosDatos.Respuesta cambiarEstado(long id, boolean activo) {
        long taller=sesion.tallerId();
        // Serializamos las bajas de administradores para que siempre quede al menos uno activo.
        talleres.findLockedById(taller).orElseThrow();
        Usuario usuario=usuarios.findByIdAndTallerId(id,taller)
            .orElseThrow(()->new RecursoNoEncontradoException("No existe el usuario solicitado."));
        if(usuario.getActivo() && !activo && usuario.getTipoUsuarioId()==Rol.ADMIN.id()
                && usuarios.countByTallerIdAndTipoUsuarioIdAndActivoTrue(taller,Rol.ADMIN.id())<=1) {
            throw new DatosInvalidosException("El taller debe conservar al menos un administrador activo.");
        }
        if(activo && usuario.getMecanicoId()!=null) {
            mecanicos.findLockedByIdAndTallerId(usuario.getMecanicoId(),taller).filter(Mecanico::getActivo)
                .orElseThrow(()->new DatosInvalidosException("Primero activá al mecánico vinculado."));
        }
        if(usuario.getActivo()!=activo) { usuario.setActivo(activo); usuario.revocarTokens(); }
        return UsuariosDatos.Respuesta.desde(usuario);
    }
}
