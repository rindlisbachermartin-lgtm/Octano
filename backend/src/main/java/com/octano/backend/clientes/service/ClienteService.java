package com.octano.backend.clientes.service;

import com.octano.backend.autenticacion.security.SesionActual;
import com.octano.backend.clientes.dto.ClientesDatos;
import com.octano.backend.clientes.entity.Cliente;
import com.octano.backend.clientes.repository.ClienteRepository;
import com.octano.backend.compartido.exception.RecursoNoEncontradoException;
import com.octano.backend.compartido.validation.CuitValidator;
import com.octano.backend.condicionesiva.repository.CondicionIvaRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClienteService {
    private final ClienteRepository clientes;
    private final CondicionIvaRepository condiciones;
    private final SesionActual sesion;
    public ClienteService(ClienteRepository clientes, CondicionIvaRepository condiciones, SesionActual sesion) {
        this.clientes = clientes; this.condiciones = condiciones; this.sesion = sesion;
    }
    public ClientesDatos.Pagina listar(String q, Boolean activo, int pagina, int tamano) {
        long tallerId = sesion.tallerId();
        String busqueda = q.strip();
        long total = clientes.contar(tallerId, busqueda, activo);
        var contenido = clientes.buscar(tallerId, busqueda, activo, pagina, tamano).stream().map(ClientesDatos.Respuesta::desde).toList();
        return new ClientesDatos.Pagina(contenido, pagina, tamano, total, (total + tamano - 1) / tamano);
    }
    public ClientesDatos.Respuesta obtener(long id) { return ClientesDatos.Respuesta.desde(buscar(id, false)); }

    @Transactional
    public ClientesDatos.Respuesta guardar(Long id, ClientesDatos.Solicitud solicitud) {
        Cliente cliente = id == null ? new Cliente() : buscar(id, true);
        if (condiciones.findById(solicitud.condicionIvaId()).isEmpty()) {
            throw new RecursoNoEncontradoException("No existe la condición de IVA seleccionada.");
        }
        if (solicitud.documento().length() == 11) CuitValidator.validar(solicitud.documento());
        if (id == null) { cliente.setTallerId(sesion.tallerId()); cliente.setActivo(true); }
        cliente.setNombre(solicitud.nombre()); cliente.setDocumento(solicitud.documento());
        cliente.setTelefono(solicitud.telefono()); cliente.setEmail(solicitud.email());
        cliente.setCalle(solicitud.calle()); cliente.setAltura(solicitud.altura());
        cliente.setCondicionIvaId(solicitud.condicionIvaId());
        registrarModificacion(cliente);
        return ClientesDatos.Respuesta.desde(clientes.save(cliente));
    }
    @Transactional
    public ClientesDatos.Respuesta cambiarEstado(long id, boolean activo) {
        Cliente cliente = buscar(id, true);
        cliente.setActivo(activo);
        registrarModificacion(cliente);
        return ClientesDatos.Respuesta.desde(clientes.save(cliente));
    }
    private Cliente buscar(long id, boolean bloquear) {
        return clientes.findByIdAndTallerId(id, sesion.tallerId(), bloquear)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cliente solicitado."));
    }
    private void registrarModificacion(Cliente cliente) {
        cliente.setFechaModif(Instant.now());
        cliente.setUsuarioModificacionId(sesion.usuarioId());
    }
}
