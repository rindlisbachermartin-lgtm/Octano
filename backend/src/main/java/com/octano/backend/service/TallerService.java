package com.octano.backend.service;

import com.octano.backend.dto.TallerRespuesta;
import com.octano.backend.dto.TallerSolicitud;
import com.octano.backend.entity.CondicionIva;
import com.octano.backend.entity.Taller;
import com.octano.backend.exception.DatosInvalidosException;
import com.octano.backend.exception.RecursoNoEncontradoException;
import com.octano.backend.repository.CondicionIvaRepository;
import com.octano.backend.repository.TallerRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TallerService {
    private final TallerRepository talleres;
    private final CondicionIvaRepository condiciones;

    public TallerService(TallerRepository talleres, CondicionIvaRepository condiciones) {
        this.talleres = talleres;
        this.condiciones = condiciones;
    }

    public List<TallerRespuesta> listar() {
        return talleres.findAll(Sort.by("id")).stream().map(TallerRespuesta::desde).toList();
    }

    public TallerRespuesta obtener(long id) { return TallerRespuesta.desde(buscar(id)); }

    @Transactional
    public TallerRespuesta crear(TallerSolicitud solicitud) {
        validarCuit(solicitud.cuit());
        CondicionIva condicion = buscarCondicion(solicitud.condicionIvaId());
        Taller taller = new Taller(solicitud.nombre(), solicitud.calle(), solicitud.altura(), solicitud.telefono(),
                solicitud.cuit(), solicitud.ingresosBrutos(), solicitud.inicioActividades(), condicion);
        return TallerRespuesta.desde(talleres.save(taller));
    }

    @Transactional
    public TallerRespuesta actualizar(long id, TallerSolicitud solicitud) {
        Taller taller = buscar(id);
        validarCuit(solicitud.cuit());
        CondicionIva condicion = buscarCondicion(solicitud.condicionIvaId());
        taller.actualizar(solicitud.nombre(), solicitud.calle(), solicitud.altura(), solicitud.telefono(),
                solicitud.cuit(), solicitud.ingresosBrutos(), solicitud.inicioActividades(), condicion);
        return TallerRespuesta.desde(taller);
    }

    private Taller buscar(long id) {
        return talleres.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe el taller solicitado."));
    }

    private CondicionIva buscarCondicion(long id) {
        return condiciones.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la condición de IVA seleccionada."));
    }

    private void validarCuit(String cuit) {
        if (cuit == null || !cuit.matches("[0-9]{11}")) {
            throw new DatosInvalidosException("El CUIT debe tener 11 números.");
        }
        int[] pesos = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
        int suma = 0;
        for (int i = 0; i < pesos.length; i++) suma += (cuit.charAt(i) - '0') * pesos[i];
        int verificador = (11 - suma % 11) % 11;
        if (verificador != cuit.charAt(10) - '0') {
            throw new DatosInvalidosException("El CUIT no tiene un dígito verificador válido.");
        }
    }
}
