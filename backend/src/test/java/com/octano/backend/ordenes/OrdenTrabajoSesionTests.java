package com.octano.backend.ordenes;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.ordenes.dto.OrdenesDatos.*;
import com.octano.backend.ordenes.repository.OrdenTrabajoRepository;
import com.octano.backend.ordenes.service.OrdenTrabajoService;
import com.octano.backend.talleres.entity.Taller;
import com.octano.backend.talleres.repository.TallerRepository;
import com.octano.backend.usuarios.entity.Rol;
import com.octano.backend.usuarios.entity.Usuario;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrdenTrabajoSesionTests {
    @Test void unMecanicoSinVinculoNoPuedeConsultarNiModificarOrdenes() {
        // La base ya impide guardar este usuario. Simulamos una sesión inválida sin debilitar esa restricción.
        var usuario=new Usuario();
        usuario.setTipoUsuarioId(Rol.MECANICO.id());
        usuario.setTallerId(1L);
        usuario.setMecanicoId(null);
        var sesion=mock(SesionActual.class);
        when(sesion.usuario()).thenReturn(usuario);
        when(sesion.tallerId()).thenReturn(1L);
        var taller=mock(TallerRepository.class);
        when(taller.findLockedById(1L)).thenReturn(Optional.of(mock(Taller.class)));
        var ordenes=mock(OrdenTrabajoRepository.class);
        var servicio=new OrdenTrabajoService(ordenes,taller,null,null,null,null,null,null,null,sesion);

        // Un filtro vacío nunca puede significar «mostrar todas» para un mecánico sin asignación.
        for(Long filtro:new Long[]{null,99L}) {
            assertThrows(AccessDeniedException.class,()->servicio.listar("",null,null,null,filtro,null,null,0,20));
        }
        assertThrows(AccessDeniedException.class,()->servicio.obtener(1));
        assertThrows(AccessDeniedException.class,()->servicio.iniciar(1));
        assertThrows(AccessDeniedException.class,()->servicio.trabajo(1,new Trabajo("Intento",1000,"")));
        assertThrows(AccessDeniedException.class,()->servicio.finalizar(1,new Cierre(1000,"","",List.of())));
        assertThrows(AccessDeniedException.class,()->servicio.agregarItems(1,List.of()));
        assertThrows(AccessDeniedException.class,()->servicio.cantidad(1,1,BigDecimal.ONE));
        assertThrows(AccessDeniedException.class,()->servicio.tareaEstado(1,1,true));
        assertThrows(AccessDeniedException.class,()->servicio.foto(1,1));
        assertThrows(AccessDeniedException.class,()->servicio.quitarFoto(1,1));
        // La validación debe ocurrir antes de leer o modificar cualquier orden.
        verifyNoInteractions(ordenes);
    }
}
