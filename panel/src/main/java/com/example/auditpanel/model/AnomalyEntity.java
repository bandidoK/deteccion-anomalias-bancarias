package com.example.auditpanel.model;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "anomalies")
public class AnomalyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "timestamp_detection", nullable = false)
    private Instant timestampDetection;

    @Column(name = "tipo_anomalia", nullable = false)
    private String tipoAnomalia;

    @Column(name = "severidad", nullable = false)
    private String severidad;

    @Column(name = "entidad_sospechosa")
    private String entidadSospechosa;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "ano_id", nullable = true)
    private String anoId;

    @Column(name = "categoria", nullable = true)
    private String categoria;

    @Column(name = "cuenta_id", nullable = true)
    private String cuentaId;

    public AnomalyEntity() {
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getTimestampDetection() { return timestampDetection; }
    public void setTimestampDetection(Instant timestampDetection) { this.timestampDetection = timestampDetection; }
    public String getTipoAnomalia() { return tipoAnomalia; }
    public void setTipoAnomalia(String tipoAnomalia) { this.tipoAnomalia = tipoAnomalia; }
    public String getSeveridad() { return severidad; }
    public void setSeveridad(String severidad) { this.severidad = severidad; }
    public String getEntidadSospechosa() { return entidadSospechosa; }
    public void setEntidadSospechosa(String entidadSospechosa) { this.entidadSospechosa = entidadSospechosa; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getAnoId() { return anoId; }
    public void setAnoId(String anoId) { this.anoId = anoId; }
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    public String getCuentaId() { return cuentaId; }
    public void setCuentaId(String cuentaId) { this.cuentaId = cuentaId; }
}
