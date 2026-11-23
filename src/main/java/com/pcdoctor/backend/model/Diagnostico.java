package com.pcdoctor.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "diagnosticos")
public class Diagnostico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_diagnostico")
    private Long idDiagnostico;

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(name = "causa_probable", nullable = false, columnDefinition = "TEXT")
    private String causaProbable;

    @Column(name = "solucion_recomendada", nullable = false, columnDefinition = "TEXT")
    private String solucionRecomendada;

    @Column(name = "presupuesto_estimado_clp", nullable = false, precision = 10, scale = 2)
    private BigDecimal presupuestoEstimadoClp;

    public Diagnostico() {}

    public Long getIdDiagnostico() { return idDiagnostico; }
    public void setIdDiagnostico(Long idDiagnostico) { this.idDiagnostico = idDiagnostico; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getCausaProbable() { return causaProbable; }
    public void setCausaProbable(String causaProbable) { this.causaProbable = causaProbable; }

    public String getSolucionRecomendada() { return solucionRecomendada; }
    public void setSolucionRecomendada(String solucionRecomendada) { this.solucionRecomendada = solucionRecomendada; }

    public BigDecimal getPresupuestoEstimadoClp() { return presupuestoEstimadoClp; }
    public void setPresupuestoEstimadoClp(BigDecimal presupuestoEstimadoClp) { this.presupuestoEstimadoClp = presupuestoEstimadoClp; }
}