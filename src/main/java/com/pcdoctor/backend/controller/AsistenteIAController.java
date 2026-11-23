package com.pcdoctor.backend.controller;

import com.pcdoctor.backend.service.AsistenteIAService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping({"/api/ia", "/api/asistente"})
@CrossOrigin(origins = "*")
public class AsistenteIAController {

    private final AsistenteIAService asistenteIAService;

    public AsistenteIAController(AsistenteIAService asistenteIAService) {
        this.asistenteIAService = asistenteIAService;
    }

    @PostMapping("/consultar")
    public ResponseEntity<?> consultarIA(@RequestBody Map<String, Object> payload) {
        String prompt = (payload != null && payload.get("prompt") != null)
                ? payload.get("prompt").toString()
                : "";
        String sessionId = (payload != null && payload.get("sessionId") != null)
                ? payload.get("sessionId").toString()
                : "SESSION_" + System.currentTimeMillis();

        Map<String, Object> resultado = asistenteIAService.procesarChatIA(prompt, sessionId);
        return ResponseEntity.ok(resultado);
    }
}