package com.example.banco_fraude.controller;

import com.example.banco_fraude.model.Usuario;
import com.example.banco_fraude.model.Transaccion;
import com.example.banco_fraude.repository.UsuarioRepository;
import com.example.banco_fraude.repository.TransaccionRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final UsuarioRepository usuarioRepo;
    private final TransaccionRepository transaccionRepo;

    public AuditController(UsuarioRepository usuarioRepo, TransaccionRepository transaccionRepo) {
        this.usuarioRepo = usuarioRepo;
        this.transaccionRepo = transaccionRepo;
    }

    @GetMapping("/export-sql")
    public ResponseEntity<String> descargarDumpSQL() {
        StringBuilder sql = new StringBuilder();
        
        sql.append("-- ==========================================================\n");
        sql.append("-- AUDITORÍA FORENSE BANCARIA - DATASET SINTÉTICO METALES    --\n");
        sql.append("-- PROYECTO DESARROLLADO POR: JUAN CARLOS (MÓDULO 3)         --\n");
        sql.append("-- ==========================================================\n\n");

        // 1. Exportar Usuarios
        List<Usuario> usuarios = usuarioRepo.findAll();
        sql.append("-- ----------------------------------------------------------\n");
        sql.append("-- REGISTROS DE LA TABLA: USUARIOS\n");
        sql.append("-- ----------------------------------------------------------\n");
        for (Usuario u : usuarios) {
            sql.append(String.format(
                "INSERT INTO usuarios (id, username, fecha_registro, reputacion, pais) VALUES (%d, '%s', '%s', %d, '%s');\n",
                u.getId(), u.getUsername(), u.getFechaRegistro() != null ? u.getFechaRegistro().toString() : "", u.getReputacion(), u.getPais()
            ));
        }

        // 2. Exportar Transacciones
        List<Transaccion> transacciones = transaccionRepo.findAll();
        sql.append("\n-- ----------------------------------------------------------\n");
        sql.append("-- REGISTROS DE LA TABLA: TRANSACCIONES\n");
        sql.append("-- ----------------------------------------------------------\n");
        for (Transaccion t : transacciones) {
            sql.append(String.format(
                "INSERT INTO transacciones (id, usuario_id, monto, moneda, tipo, fecha, hash_destino) VALUES (%d, %d, %.2f, '%s', '%s', '%s', '%s');\n",
                t.getId(), t.getUsuarioId(), t.getMonto(), t.getMoneda(), t.getTipo(), t.getFecha() != null ? t.getFecha().toString() : "", t.getHashDestino()
            ));
        }

        System.out.println("✓ Solicitud de auditoría procesada: " + transacciones.size() + " movimientos exportados.");

        String contenidoArchivo = sql.toString();
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=verdad_oculta_banco.sql")
                .contentType(MediaType.TEXT_PLAIN)
                .body(contenidoArchivo);
    }
}
