package com.example.banco_fraude.service;

import com.example.banco_fraude.model.Usuario;
import com.example.banco_fraude.model.Transaccion;
import com.example.banco_fraude.repository.UsuarioRepository;
import com.example.banco_fraude.repository.TransaccionRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
public class DataGeneratorService {

    private final UsuarioRepository usuarioRepo;
    private final TransaccionRepository transaccionRepo;

    public DataGeneratorService(UsuarioRepository usuarioRepo, TransaccionRepository transaccionRepo) {
        this.usuarioRepo = usuarioRepo;
        this.transaccionRepo = transaccionRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void generarDatosSinteticos() {
        System.out.println("======================================================");
        System.out.println("INICIANDO GENERACIÓN DE LOGS BANCARIOS SINTÉTICOS...");
        System.out.println("======================================================");

        Random rng = new Random();
        long idTransaccion = 1;

        // 1. GENERAR RUIDO DE FONDO (150 usuarios honestos)
        for (long i = 1; i <= 150; i++) {
            Usuario u = new Usuario(
                i, 
                "user_legit_" + i, 
                LocalDateTime.now().minusDays(rng.nextInt(30)), 
                rng.nextInt(40) + 60, 
                "España"
            );
            usuarioRepo.save(u);

            int cantidadTransacciones = rng.nextInt(2) + 3; 
            for (int j = 0; j < cantidadTransacciones; j++) {
                double montoNormal = 15.50 + rng.nextInt(120);
                Transaccion t = new Transaccion(
                    idTransaccion++, 
                    u.getId(), 
                    montoNormal, 
                    "EUR", 
                    "RETIRO", 
                    LocalDateTime.now().minusDays(rng.nextInt(5)).minusHours(rng.nextInt(24)), 
                    "0xLegitHash" + rng.nextInt(99999)
                );
                transaccionRepo.save(t);
            }
        }
        System.out.println("✓ 150 usuarios honestos y su historial cargados.");

        // 2. INYECCIÓN DE PATRONES OCULTOS

        // PATRÓN 1: El Pitufeo (Usuario 500)
        Usuario pitufeador = new Usuario(500L, "crypto_king99", LocalDateTime.now().minusDays(10), 80, "Andorra");
        usuarioRepo.save(pitufeador);

        LocalDateTime horaPitufeo = LocalDateTime.now().minusDays(2);
        for (int k = 0; k < 12; k++) {
            Transaccion t = new Transaccion(
                idTransaccion++, 
                500L, 
                9950.00, 
                "EUR", 
                "RETIRO", 
                horaPitufeo.plusMinutes(k * 3), 
                "0xAnonymTarget" + k
            );
            transaccionRepo.save(t);
        }
        System.out.println("⚠ [PATRÓN INYECTADO]: Pitufeo en usuario 500 listo.");

        // PATRÓN 2: Cuenta Mula (Usuario 600)
        Usuario mula = new Usuario(600L, "mula_bancaria01", LocalDateTime.now().minusDays(1), 10, "Desconocido");
        usuarioRepo.save(mula);

        LocalDateTime horaIngresoMula = LocalDateTime.now().minusHours(6);
        transaccionRepo.save(new Transaccion(
            idTransaccion++, 
            600L, 300000.00, 
            "EUR", 
            "DEPOSITO", 
            horaIngresoMula, 
            "0xOrigenDesconocido"
        ));

        for (int m = 0; m < 15; m++) {
            Transaccion t = new Transaccion(
                idTransaccion++, 
                600L, 
                20000.00, 
                "EUR", 
                "RETIRO", 
                horaIngresoMula.plusMinutes(15 + (m * 2)), 
                "0xCuentaDestinoOculta" + m
            );
            transaccionRepo.save(t);
        }
        System.out.println("⚠ [PATRÓN INYECTADO]: Cuenta Mula en usuario 600 listo.");
        System.out.println("======================================================");
    }
}
