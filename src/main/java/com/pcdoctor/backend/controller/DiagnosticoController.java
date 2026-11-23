package com.pcdoctor.backend.controller;

import com.pcdoctor.backend.model.Diagnostico;
import com.pcdoctor.backend.repository.DiagnosticoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/diagnosticos", "/api/diagnostico"})
@CrossOrigin(origins = "*")
@SuppressWarnings("null")
public class DiagnosticoController {

    private final DiagnosticoRepository diagnosticoRepository;

    public DiagnosticoController(DiagnosticoRepository diagnosticoRepository) {
        this.diagnosticoRepository = diagnosticoRepository;
    }

    @GetMapping
    public List<Diagnostico> listarTodos() {
        return diagnosticoRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Diagnostico> obtenerPorId(@PathVariable Long id) {
        return diagnosticoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarDiagnostico(@PathVariable Long id,
                                                   @RequestHeader(value = "X-Auth-Token", required = false) String token,
                                                   @RequestBody Map<String, Object> payload) {
        String rol = AuthController.obtenerRolPorToken(token);
        if (!"ADMINISTRADOR".equals(rol)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Acceso denegado (Rol: " + rol + "). Solo ADMINISTRADOR puede modificar tarifas del catálogo."));
        }

        return diagnosticoRepository.findById(id).map(diag -> {
            if (payload.get("titulo") != null) {
                diag.setTitulo(payload.get("titulo").toString());
            }
            if (payload.get("causaProbable") != null) {
                diag.setCausaProbable(payload.get("causaProbable").toString());
            }
            if (payload.get("solucionRecomendada") != null) {
                diag.setSolucionRecomendada(payload.get("solucionRecomendada").toString());
            }
            if (payload.get("presupuestoEstimadoClp") != null) {
                diag.setPresupuestoEstimadoClp(new BigDecimal(payload.get("presupuestoEstimadoClp").toString()));
            }
            Diagnostico actualizado = diagnosticoRepository.save(diag);
            return ResponseEntity.ok(actualizado);
        }).orElse(ResponseEntity.notFound().build());
    }
}