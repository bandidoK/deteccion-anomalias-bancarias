package com.example.banco_fraude;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@SpringBootApplication
@RestController
@RequestMapping("/api/audit")
public class BancoFraudeApplication {

    private final MyUsuarioRepository usuarioRepo;
    private final MyTransaccionRepository transaccionRepo;

    public BancoFraudeApplication(MyUsuarioRepository usuarioRepo, MyTransaccionRepository transaccionRepo) {
        this.usuarioRepo = usuarioRepo;
        this.transaccionRepo = transaccionRepo;
    }

    public static void main(String[] args) {
        SpringApplication.run(BancoFraudeApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void generarDatosSinteticos() {
        System.out.println("======================================================");
        System.out.println("INICIANDO GENERACIÓN DE LOGS BANCARIOS SINTÉTICOS...");
        System.out.println("======================================================");

        Random rng = new Random();
        long idTransaccion = 1;

        // 1. Generar ruido de fondo (150 usuarios honestos)
        for (long i = 1; i <= 150; i++) {
            MiniUsuario u = new MiniUsuario(i, "user_legit_" + i, LocalDateTime.now().minusDays(rng.nextInt(30)), rng.nextInt(40) + 60, "España");
            usuarioRepo.save(u);

            int cantidadTransacciones = rng.nextInt(2) + 3; 
            for (int j = 0; j < cantidadTransacciones; j++) {
                double montoNormal = 15.50 + rng.nextInt(120);
                MiniTransaccion t = new MiniTransaccion(idTransaccion++, u.getId(), montoNormal, "EUR", "RETIRO", LocalDateTime.now().minusDays(rng.nextInt(5)).minusHours(rng.nextInt(24)), "0xLegitHash" + rng.nextInt(99999));
                transaccionRepo.save(t);
            }
        }
        System.out.println("✓ 150 usuarios honestos cargados.");

        // PATRÓN 1: El Pitufeo (Usuario 500)
        usuarioRepo.save(new MiniUsuario(500L, "crypto_king99", LocalDateTime.now().minusDays(10), 80, "Andorra"));
        for (int k = 0; k < 12; k++) {
            transaccionRepo.save(new MiniTransaccion(idTransaccion++, 500L, 9950.00, "EUR", "RETIRO", LocalDateTime.now().minusDays(2).plusMinutes(k * 3), "0xAnonymTarget" + k));
        }
        System.out.println("⚠ [PATRÓN INYECTADO]: Pitufeo en usuario 500 listo.");

        // PATRÓN 2: Cuenta Mula (Usuario 600)
        usuarioRepo.save(new MiniUsuario(600L, "mula_bancaria01", LocalDateTime.now().minusDays(1), 10, "Desconocido"));
        transaccionRepo.save(new MiniTransaccion(idTransaccion++, 600L, 300000.00, "EUR", "DEPOSITO", LocalDateTime.now().minusHours(6), "0xOrigenDesconocido"));
        for (int m = 0; m < 15; m++) {
            transaccionRepo.save(new MiniTransaccion(idTransaccion++, 600L, 20000.00, "EUR", "RETIRO", LocalDateTime.now().minusHours(6).plusMinutes(15 + (m * 2)), "0xCuentaDestinoOculta" + m));
        }
        System.out.println("⚠ [PATRÓN INYECTADO]: Cuenta Mula en usuario 600 listo.");
        System.out.println("======================================================");
    }

    @GetMapping("/export-sql")
    public ResponseEntity<String> descargarDumpSQL() {
        StringBuilder sql = new StringBuilder();
        
        sql.append("-- ==========================================================\n");
        sql.append("-- AUDITORÍA FORENSE BANCARIA - DATASET SINTÉTICO METALES    --\n");
        sql.append("-- PROYECTO DESARROLLADO POR: JUAN CARLOS (MÓDULO 3)         --\n");
        sql.append("-- ==========================================================\n\n");

        List<MiniUsuario> usuarios = usuarioRepo.findAll();
        sql.append("-- REGISTROS DE LA TABLA: USUARIOS\n");
        for (MiniUsuario u : usuarios) {
            sql.append(String.format("INSERT INTO usuarios (id, username, fecha_registro, reputacion, pais) VALUES (%d, '%s', '%s', %d, '%s');\n", u.getId(), u.getUsername(), u.getFechaRegistro() != null ? u.getFechaRegistro().toString() : "", u.getReputacion(), u.getPais()));
        }

        List<MiniTransaccion> transacciones = transaccionRepo.findAll();
        sql.append("\n-- REGISTROS DE LA TABLA: TRANSACCIONES\n");
        for (MiniTransaccion t : transacciones) {
            sql.append(String.format("INSERT INTO transacciones (id, usuario_id, monto, moneda, tipo, fecha, hash_destino) VALUES (%d, %d, %.2f, '%s', '%s', '%s', '%s');\n", t.getId(), t.getUsuarioId(), t.getMonto(), t.getMoneda(), t.getTipo(), t.getFecha() != null ? t.getFecha().toString() : "", t.getHashDestino()));
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=verdad_oculta_banco.sql")
                .contentType(MediaType.TEXT_PLAIN)
                .body(sql.toString());
    }
}

// ==========================================
// COMPONENTES INTERNOS (Para evitar errores de rutas)
// ==========================================

@Entity
@Table(name = "usuarios")
class MiniUsuario {
    @Id private Long id;
    private String username;
    private LocalDateTime fechaRegistro;
    private int reputacion;
    private String pais;

    public MiniUsuario() {}
    public MiniUsuario(Long id, String username, LocalDateTime fechaRegistro, int reputacion, String pais) {
        this.id = id; this.username = username; this.fechaRegistro = fechaRegistro; this.reputacion = reputacion; this.pais = pais;
    }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public int getReputacion() { return reputacion; }
    public String getPais() { return pais; }
}

@Entity
@Table(name = "transacciones")
class MiniTransaccion {
    @Id private Long id;
    private Long usuarioId;
    private double monto;
    private String moneda;
    private String tipo;
    private LocalDateTime fecha;
    private String hashDestino;

    public MiniTransaccion() {}
    public MiniTransaccion(Long id, Long usuarioId, double monto, String moneda, String tipo, LocalDateTime fecha, String hashDestino) {
        this.id = id; this.usuarioId = usuarioId; this.monto = monto; this.moneda = moneda; this.tipo = tipo; this.fecha = fecha; this.hashDestino = hashDestino;
    }
    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public double getMonto() { return monto; }
    public String getMoneda() { return moneda; }
    public String getTipo() { return tipo; }
    public LocalDateTime getFecha() { return fecha; }
    public String getHashDestino() { return hashDestino; }
}

@Repository interface MyUsuarioRepository extends JpaRepository<MiniUsuario, Long> {}
@Repository interface MyTransaccionRepository extends JpaRepository<MiniTransaccion, Long> {}
