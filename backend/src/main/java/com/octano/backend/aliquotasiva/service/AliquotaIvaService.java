package com.octano.backend.aliquotasiva.service;

import com.octano.backend.aliquotasiva.AliquotaIva;
import com.octano.backend.aliquotasiva.dto.AliquotaIvaRespuesta;
import com.octano.backend.aliquotasiva.repository.AliquotaIvaRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class AliquotaIvaService {
    private final AliquotaIvaRepository repository;
    public AliquotaIvaService(AliquotaIvaRepository repository) { this.repository=repository; }
    public List<AliquotaIvaRespuesta> listar() { return repository.findAll(); }
    public void validar(BigDecimal porcentaje) { AliquotaIva.validar(porcentaje); }
}
