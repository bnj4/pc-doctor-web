package com.pcdoctor.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "solicitudes_reparacion")
public class SolicitudReparacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long idSolicitud;

    @ManyToOne
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "id_diagnostico")
    private Diagnostico diagnostico;

    @Column(name = "resumen_respuestas", columnDefinition = "TEXT")
    private String resumenRespuestas;

    @Column(name = "presupuesto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal presupuestoTotal;

    @Column(name = "fecha_visita", nullable = false)
    private LocalDate fechaVisita;

    @Column(length = 50)
    private String estado = "PENDIENTE";

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    public SolicitudReparacion() {}

    public Long getIdSolicitud() { return idSolicitud; }
    public void setIdSolicitud(Long idSolicitud) { this.idSolicitud = idSolicitud; }

    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }

    public Diagnostico getDiagnostico() { return diagnostico; }
    public void setDiagnostico(Diagnostico diagnostico) { this.diagnostico = diagnostico; }

    public String getResumenRespuestas() { return resumenRespuestas; }
    public void setResumenRespuestas(String resumenRespuestas) { this.resumenRespuestas = resumenRespuestas; }

    public BigDecimal getPresupuestoTotal() { return presupuestoTotal; }
    public void setPresupuestoTotal(BigDecimal presupuestoTotal) { this.presupuestoTotal = presupuestoTotal; }

    public LocalDate getFechaVisita() { return fechaVisita; }
    public void setFechaVisita(LocalDate fechaVisita) { this.fechaVisita = fechaVisita; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}