package com.example.banco_fraude.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transacciones")
public class Transaccion {
    @Id
    private Long id;
    private Long usuarioId;
    private double monto;
    private String moneda;
    private String tipo; 
    private LocalDateTime fecha;
    private String hashDestino;

    // Constructor vacío obligatorio
    public Transaccion() {}

    // Constructor completo
    public Transaccion(Long id, Long usuarioId, double monto, String moneda, String tipo, LocalDateTime fecha, String hashDestino) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.monto = monto;
        this.moneda = moneda;
        this.tipo = tipo;
        this.fecha = fecha;
        this.hashDestino = hashDestino;
    }

    // Getters y Setters tradicionales
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public double getMonto() { return monto; }
    public void setMonto(double monto) { this.monto = monto; }
    public String getMoneda() { return moneda; }
    public void setMoneda(String moneda) { this.moneda = moneda; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public String getHashDestino() { return hashDestino; }
    public void setHashDestino(String hashDestino) { this.hashDestino = hashDestino; }
}
