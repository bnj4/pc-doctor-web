package com.pcdoctor.backend.controller;

import com.pcdoctor.backend.model.Pregunta;
import com.pcdoctor.backend.repository.PreguntaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/preguntas")
@CrossOrigin(origins = "*")
@SuppressWarnings("null")
public class PreguntaController {

    private final PreguntaRepository preguntaRepository;

    public PreguntaController(PreguntaRepository preguntaRepository) {
        this.preguntaRepository = preguntaRepository;
    }

    @GetMapping
    public List<Pregunta> listarTodas() {
        return preguntaRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pregunta> obtenerPorId(@PathVariable Long id) {
        return preguntaRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sintoma/{idSintoma}")
    public List<Pregunta> obtenerPorSintoma(@PathVariable Long idSintoma) {
        return preguntaRepository.findByIdSintoma(idSintoma);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarPregunta(@PathVariable Long id,
                                                @RequestHeader(value = "X-Auth-Token", required = false) String token,
                                                @RequestBody Map<String, Object> payload) {
        String rol = AuthController.obtenerRolPorToken(token);
        if (!"ADMINISTRADOR".equals(rol)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Acceso denegado (Rol: " + rol + "). Solo ADMINISTRADOR puede editar el árbol de preguntas."));
        }

        return preguntaRepository.findById(id).map(p -> {
            if (payload.get("textoPregunta") != null) {
                p.setTextoPregunta(payload.get("textoPregunta").toString());
            }
            Pregunta actualizada = preguntaRepository.save(p);
            return ResponseEntity.ok(actualizada);
        }).orElse(ResponseEntity.notFound().build());
    }
}