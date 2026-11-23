package com.pcdoctor.backend.controller;

import com.pcdoctor.backend.model.Cliente;
import com.pcdoctor.backend.model.Diagnostico;
import com.pcdoctor.backend.model.SolicitudReparacion;
import com.pcdoctor.backend.repository.ClienteRepository;
import com.pcdoctor.backend.repository.DiagnosticoRepository;
import com.pcdoctor.backend.repository.SolicitudReparacionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/api/solicitudes", "/api/solicitud"})
@CrossOrigin(origins = "*")
@SuppressWarnings("null")
public class SolicitudReparacionController {

    private final SolicitudReparacionRepository solicitudRepository;
    private final ClienteRepository clienteRepository;
    private final DiagnosticoRepository diagnosticoRepository;

    public SolicitudReparacionController(SolicitudReparacionRepository solicitudRepository,
                                         ClienteRepository clienteRepository,
                                         DiagnosticoRepository diagnosticoRepository) {
        this.solicitudRepository = solicitudRepository;
        this.clienteRepository = clienteRepository;
        this.diagnosticoRepository = diagnosticoRepository;
    }

    @GetMapping
    public ResponseEntity<?> listarTodas(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        String rol = AuthController.obtenerRolPorToken(token);
        if ("USUARIO".equals(rol)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Acceso restringido: se requiere iniciar sesión como Técnico o Administrador."));
        }
        return ResponseEntity.ok(solicitudRepository.findAll());
    }

    // Endpoint público para que el Cliente consulte en vivo el estado de sus solicitudes por RUT
    @GetMapping("/cliente/{rut}")
    public ResponseEntity<?> listarPorCliente(@PathVariable String rut) {
        String rutBuscado = rut.replace(".", "").replace("-", "").trim().toUpperCase();
        List<SolicitudReparacion> filtradas = solicitudRepository.findAll().stream()
                .filter(s -> s.getCliente() != null && s.getCliente().getRut() != null)
                .filter(s -> s.getCliente().getRut().replace(".", "").replace("-", "").trim().toUpperCase().equals(rutBuscado))
                .toList();
        return ResponseEntity.ok(filtradas);
    }

    @PostMapping
    public ResponseEntity<?> crearSolicitud(@RequestBody Map<String, Object> payload) {
        try {
            String rut = AuthController.normalizarRut((String) payload.get("rut"));
            if (!AuthController.validarRutModulo11(rut)) {
                return ResponseEntity.badRequest().body(Map.of("error", "El RUT ingresado no es válido según Módulo 11."));
            }

            String nombre = (String) payload.get("nombre");
            String correo = (String) payload.get("correo");
            String telefono = (String) payload.get("telefono");

            Optional<Cliente> clienteOpt = clienteRepository.findByRut(rut);
            Cliente cliente;
            if (clienteOpt.isPresent()) {
                cliente = clienteOpt.get();
                cliente.setNombre(nombre);
                cliente.setCorreo(correo);
                cliente.setTelefono(telefono);
            } else {
                cliente = new Cliente();
                cliente.setRut(rut);
                cliente.setNombre(nombre);
                cliente.setCorreo(correo);
                cliente.setTelefono(telefono);
            }
            cliente = clienteRepository.save(cliente);

            Long idDiagnostico = null;
            if (payload.get("idDiagnostico") != null) {
                idDiagnostico = Long.parseLong(payload.get("idDiagnostico").toString());
            }

            Diagnostico diagnostico = null;
            if (idDiagnostico != null) {
                diagnostico = diagnosticoRepository.findById(idDiagnostico).orElse(null);
            }

            SolicitudReparacion solicitud = new SolicitudReparacion();
            solicitud.setCliente(cliente);
            solicitud.setDiagnostico(diagnostico);
            solicitud.setResumenRespuestas((String) payload.get("resumenRespuestas"));

            Object presObj = payload.get("presupuestoTotal");
            BigDecimal presupuesto = BigDecimal.ZERO;
            if (presObj != null) {
                presupuesto = new BigDecimal(presObj.toString());
            }
            solicitud.setPresupuestoTotal(presupuesto);

            if (payload.get("fechaVisita") != null) {
                solicitud.setFechaVisita(LocalDate.parse(payload.get("fechaVisita").toString()));
            }

            solicitud.setEstado("PENDIENTE");

            SolicitudReparacion guardada = solicitudRepository.save(solicitud);
            return ResponseEntity.ok(guardada);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error al procesar el agendamiento: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarSolicitud(@PathVariable Long id,
                                                 @RequestHeader(value = "X-Auth-Token", required = false) String token,
                                                 @RequestBody Map<String, Object> payload) {
        String rol = AuthController.obtenerRolPorToken(token);
        if ("USUARIO".equals(rol)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No autorizado: un Usuario estándar no puede modificar solicitudes."));
        }

        if ("TECNICO".equals(rol) && (payload.get("presupuestoTotal") != null || payload.get("fechaVisita") != null)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Permiso denegado (Rol: TECNICO). Solo el ADMINISTRADOR puede modificar presupuestos o fechas."));
        }

        return solicitudRepository.findById(id).map(sol -> {
            if (payload.get("estado") != null) {
                sol.setEstado(payload.get("estado").toString());
            }
            if (payload.get("presupuestoTotal") != null) {
                sol.setPresupuestoTotal(new BigDecimal(payload.get("presupuestoTotal").toString()));
            }
            if (payload.get("fechaVisita") != null) {
                sol.setFechaVisita(LocalDate.parse(payload.get("fechaVisita").toString()));
            }
            if (payload.get("notaTecnica") != null) {
                String actual = sol.getResumenRespuestas() != null ? sol.getResumenRespuestas() : "";
                sol.setResumenRespuestas(actual + "\n\n[INFORME TÉCNICO]: " + payload.get("notaTecnica").toString());
            }
            SolicitudReparacion actualizada = solicitudRepository.save(sol);
            return ResponseEntity.ok(actualizada);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarSolicitud(@PathVariable Long id,
                                               @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        String rol = AuthController.obtenerRolPorToken(token);
        if (!"ADMINISTRADOR".equals(rol)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Acceso denegado (Rol actual: " + rol + "). Solo ADMINISTRADOR puede eliminar registros."));
        }

        if (solicitudRepository.existsById(id)) {
            solicitudRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}