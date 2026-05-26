# Formato de logs — Referencia para todos los módulos

Este documento define los campos exactos que el **generador** produce
y que el **detector** y el **panel** deben consumir. No cambiar sin
consenso del equipo.

---

## Formato de línea de log (CSV)

Cada línea del fichero de log representa una operación o evento.
Los campos están separados por `|` para evitar conflictos con comas
en el campo `concepto`.

```
transaction_id|timestamp|cuenta_origen|cuenta_destino|importe|divisa|tipo_operacion|ip_origen|dispositivo_id|pais_origen|pais_destino|sesion_id|concepto|estado
```

### Ejemplo de línea válida

```
a3f7c2d1-...|2026-05-23T14:32:01.123|ES1234567890|ES0987654321|9900.00|EUR|TRANSFERENCIA|85.34.12.200|MOB-Android-A52|ES|ES|sess-uuid|Pago factura|COMPLETADA
```

---

## Descripción de cada campo

| Campo            | Tipo           | Obligatorio | Descripción |
|------------------|----------------|-------------|-------------|
| `transaction_id` | UUID (string)  | Sí          | Identificador único de la operación. Sin repetidos. |
| `timestamp`      | ISO 8601 ms    | Sí          | `yyyy-MM-ddTHH:mm:ss.SSS` en UTC |
| `cuenta_origen`  | String (24)    | Condicional | Obligatorio en TRANSFERENCIA, RETIRO, PAGO. Vacío en DEPOSITO. |
| `cuenta_destino` | String (24)    | Condicional | Obligatorio en TRANSFERENCIA. Vacío en RETIRO_CAJERO. |
| `importe`        | Decimal (15,2) | Sí          | Siempre positivo. Máx. 2 decimales. |
| `divisa`         | ISO 4217       | Sí          | Por defecto `EUR`. |
| `tipo_operacion` | Enum           | Sí          | Ver valores permitidos abajo. |
| `ip_origen`      | IPv4 / IPv6    | Sí          | IP real o simulada. Nunca vacío. |
| `dispositivo_id` | String (100)   | Sí          | Ej: `MOB-Android-A52`, `WEB-Chrome-Win`, `ATM-0042` |
| `pais_origen`    | ISO 3166-1 a2  | Sí          | País de la IP de origen. |
| `pais_destino`   | ISO 3166-1 a2  | No          | Solo en transferencias internacionales. |
| `sesion_id`      | UUID (string)  | Sí          | Sesión activa en el momento de la operación. |
| `concepto`       | String (255)   | No          | Texto libre. Sin pipes `|`. Puede estar vacío. |
| `estado`         | Enum           | Sí          | Ver valores permitidos abajo. |

---

## Valores permitidos

### `tipo_operacion`
- `TRANSFERENCIA` — envío entre cuentas
- `RETIRO_CAJERO` — retirada en efectivo
- `DEPOSITO` — ingreso en cuenta
- `PAGO_COMERCIO` — pago en comercio físico (TPV)
- `PAGO_ONLINE` — pago en comercio online
- `CIERRE_CUENTA` — cierre de cuenta

### `estado`
- `COMPLETADA`
- `FALLIDA`
- `PENDIENTE`

---

## Formato de log de sesiones (fichero separado)

Para eventos de autenticación usar un fichero aparte `sesiones.log`:

```
sesion_id|cliente_id|timestamp|tipo_evento|ip|dispositivo_id
```

### `tipo_evento`
- `LOGIN_OK`
- `LOGIN_FALLIDO`
- `LOGOUT`
- `CAMBIO_PASSWORD`
- `CAMBIO_CONTACTO`
- `CONSULTA_SALDO`

---

## Restricciones importantes para el generador

- El `transaction_id` debe ser un UUID v4 real, nunca un contador.
- El `timestamp` de una operación debe ser **posterior** al `LOGIN_OK`
  de la misma sesión.
- El `importe` no puede ser negativo ni cero.
- Insertar al menos un 3% de operaciones anómalas conocidas para
  que el detector pueda validarse (ver `anomalias.md`).
- El fichero de qué operaciones son anómalas de verdad **no se sube
  al repositorio** (ver normas del README).

---

## Volumen esperado

| Fichero           | Filas mínimas |
|-------------------|---------------|
| `transacciones.log` | 100.000     |
| `sesiones.log`      | 20.000      |
