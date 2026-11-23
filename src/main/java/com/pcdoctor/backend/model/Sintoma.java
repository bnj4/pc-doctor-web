package com.pcdoctor.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "sintomas")
public class Sintoma {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_sintoma")
    private Long idSintoma;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 255)
    private String descripcion;

    public Sintoma() {}

    public Long getIdSintoma() { return idSintoma; }
    public void setIdSintoma(Long idSintoma) { this.idSintoma = idSintoma; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}