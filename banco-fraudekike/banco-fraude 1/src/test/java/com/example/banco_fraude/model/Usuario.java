package com.example.banco_fraude.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class Usuario {
    @Id
    private Long id;
    private String username;
    private LocalDateTime fechaRegistro;
    private int reputacion;
    private String pais;

    // Constructor vacío obligatorio para JPA
    public Usuario() {}

    // Constructor completo
    public Usuario(Long id, String username, LocalDateTime fechaRegistro, int reputacion, String pais) {
        this.id = id;
        this.username = username;
        this.fechaRegistro = fechaRegistro;
        this.reputacion = reputacion;
        this.pais = pais;
    }

    // Getters y Setters tradicionales
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public int getReputacion() { return reputacion; }
    public void setReputacion(int reputacion) { this.reputacion = reputacion; }
    public String getPais() { return pais; }
    public void setPais(String pais) { this.pais = pais; }
}