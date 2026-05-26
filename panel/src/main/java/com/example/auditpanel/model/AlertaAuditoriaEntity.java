package com.example.auditpanel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "alertas_auditoria")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertaAuditoriaEntity {

    @Id
    private Long id;

    @Column(name = "timestamp_detection")
    @JsonProperty("timestamp_detection")
    private Instant timestampDetection;

    @Column(name = "tipo_alerta")
    @JsonProperty("tipo_alerta")
    private String tipoAlerta;

    @Column(name = "severidad")
    @JsonProperty("severidad")
    private String severidad;

    @Column(name = "entidad_sospechosa")
    @JsonProperty("entidad_sospechosa")
    private String entidadSospechosa;

    @Column(name = "descripcion")
    @JsonProperty("descripcion")
    private String descripcion;

    @Column(name = "created_at")
    @JsonProperty("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getTimestampDetection() {
        return timestampDetection;
    }

    public void setTimestampDetection(Instant timestampDetection) {
        this.timestampDetection = timestampDetection;
    }

    public String getTipoAlerta() {
        return tipoAlerta;
    }

    public void setTipoAlerta(String tipoAlerta) {
        this.tipoAlerta = tipoAlerta;
    }

    public String getSeveridad() {
        return severidad;
    }

    public void setSeveridad(String severidad) {
        this.severidad = severidad;
    }

    public String getEntidadSospechosa() {
        return entidadSospechosa;
    }

    public void setEntidadSospechosa(String entidadSospechosa) {
        this.entidadSospechosa = entidadSospechosa;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
