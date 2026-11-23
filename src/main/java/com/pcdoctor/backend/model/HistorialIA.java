package com.pcdoctor.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "historial_ia")
public class HistorialIA {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long idHistorial;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "prompt_usuario", nullable = false, columnDefinition = "TEXT")
    private String promptUsuario;

    @Column(name = "respuesta_ia", nullable = false, columnDefinition = "TEXT")
    private String respuestaIa;

    @Column(name = "nivel_confianza", nullable = false)
    private String nivelConfianza;

    @Column(name = "iteracion_numero", nullable = false)
    private Integer iteracionNumero;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    public HistorialIA() {}

    public HistorialIA(String sessionId, String promptUsuario, String respuestaIa, String nivelConfianza, Integer iteracionNumero) {
        this.sessionId = sessionId;
        this.promptUsuario = promptUsuario;
        this.respuestaIa = respuestaIa;
        this.nivelConfianza = nivelConfianza;
        this.iteracionNumero = iteracionNumero;
    }

    public Long getIdHistorial() { return idHistorial; }
    public void setIdHistorial(Long idHistorial) { this.idHistorial = idHistorial; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getPromptUsuario() { return promptUsuario; }
    public void setPromptUsuario(String promptUsuario) { this.promptUsuario = promptUsuario; }

    public String getRespuestaIa() { return respuestaIa; }
    public void setRespuestaIa(String respuestaIa) { this.respuestaIa = respuestaIa; }

    public String getNivelConfianza() { return nivelConfianza; }
    public void setNivelConfianza(String nivelConfianza) { this.nivelConfianza = nivelConfianza; }

    public Integer getIteracionNumero() { return iteracionNumero; }
    public void setIteracionNumero(Integer iteracionNumero) { this.iteracionNumero = iteracionNumero; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
}