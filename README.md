# Detección de Anomalías en Transacciones Bancarias

Proyecto académico desarrollado con Java Spring Boot para la detección
de patrones anómalos en logs masivos de transacciones bancarias.

## Equipo

| Nombre | Rol |
| --- | --- |
| Juan Carlos | Generador de logs sintéticos |
| Adrián | Detector de anomalías |
| Javier | Panel de auditoría |
| Kike | Coordinación y repositorio |

## Estructura del proyecto

- `/generador` — Generación de logs sintéticos masivos
- `/detector` — Análisis y detección de patrones sospechosos
- `/panel` — Panel de auditoría con Spring Boot

## Normas del repositorio

- Nadie sube directamente a `main`
- Cada miembro trabaja en su rama propia
- Todo cambio pasa por Pull Request y aprobación del coordinador
- El fichero de verdad oculta NO se sube a este repositorio

## Tecnologías

- Java + Spring Boot
- SQL / Base de datos relacional
- HTML/CSS (panel web)

## Registro de cambios

### v0.5 — 26/05/2026 (Kike — coordinación)

- **Reestructuración del repositorio:** la carpeta `deteccion-anomalias-bancarias-main/`
  que se había creado anidada dentro del repo se ha aplanado a la raíz. Los módulos
  `/panel`, `/generador` y `/detector` ahora están directamente en la raíz del proyecto.
- **`.gitignore` reforzado:** añadidas entradas `verdad_oculta*.csv` (fichero que
  NUNCA debe subirse al repositorio — entrega directa al profesor) y `.claude/`.
- **Problema de coordinación — módulo `/generador` (Juan Carlos):**
  Juan Carlos aún no ha subido su trabajo a la rama `rama-juancarlos`. El módulo
  `/generador` solo contiene el fichero `.gitkeep` de marcador. Pendiente de entrega.
- **Problema de coordinación — módulo `/detector` (Adrián):**
  Adrián aún no ha subido su trabajo a la rama `rama-adrian`. El módulo
  `/detector` solo contiene el fichero `.gitkeep` de marcador. Pendiente de entrega.
  **Importante:** Adrián no debe tener acceso al fichero `verdad_oculta*.csv`
  hasta que haya completado y subido su análisis de forma independiente.
- **Incidencia técnica:** los cambios del día 25/05 se subieron manualmente al repo
  fuera del flujo habitual (chat VS Code en lugar del proceso de rama + PR acordado),
  lo que generó una historia de commits huérfana localmente. Corregido hoy mediante
  rebase sobre `origin/main` antes de empujar.

### v0.4 — 25/05/2026 (Kike — coordinación)

- **`anomalias.md` reestructurado** como Entregable 2 completo siguiendo el formato
  del caso SociaMunicipal del profesor:
  - Tabla de contexto del proyecto
  - Tarea 1: Inventario de 10 activos (A-01..A-10) con propietario y dimensiones CIA
  - Tarea 2: Análisis amenaza/vulnerabilidad/riesgo/impacto/control por módulo
    (generador, detector, panel, datos y credenciales)
  - Tarea 3: Matriz 7 módulos × 7 dimensiones de seguridad (ENS)
  - Tarea 4: Catálogo de 20 riesgos con código R01..R20, probabilidad, impacto
    y severidad
  - Tarea 5: 10 principios reales de seguridad (coste, imposibilidad, proporcionalidad,
    redundancia, asimetría, incomodidad, conocimiento del mal, privilegio mínimo,
    sospecha, resiliencia) aplicados al proyecto bancario
  - Tarea 6: 10 escenarios de ataque o fallo completos (13 campos cada uno):
    IDOR en panel, log injection, fuerza bruta, verdad oculta expuesta, Actuator
    en prod, XSS en concepto, DoS batch, credenciales en repo, JWT robado,
    evasión adversarial del detector
  - Priorización P×I×F con tabla ordenada por prioridad (R11 y R04 en tope = 100)
  - Controles propuestos: preventivos (CP), detectivos (CD), correctivos (CC),
    compensatorios (CK) y organizativos (CO)
  - Conclusión crítica: 3 riesgos bloqueantes, controles mínimos no negociables,
    riesgos residuales aceptados y estrategia de resiliencia de 4 niveles
  - Fuentes: F-01..F-10 (OWASP, Spring Security, Ley 10/2010, RGPD, ENS, NIST)
  - El contenido anterior (catálogo ANO-001..054 + sección de integración) pasa a
    Apéndice A y Apéndice B
- **Panel de Javi (`/panel`) adaptado** al proyecto bancario:
  - Nuevo `BankingTransactionEvent.java` — modelo con los 14 campos del formato
    pipe-separated definido en `formato-logs.md`
  - Nuevo `BankingAnomalyDetectorService.java` — implementa **25 reglas bancarias**
    con detección stateful en memoria (ConcurrentHashMap + ArrayDeque):
    - Integridad de datos: ANO-031 (timestamp vacío), ANO-032 (importe ≤ 0),
      ANO-033 (tx_id duplicado), ANO-034 (sin ip_origen), ANO-035 (sin sesion_id),
      ANO-038 (divisa inválida), ANO-039 (> 2 decimales), ANO-040 (SQL/XSS en concepto)
    - Fraude financiero: ANO-001 (smurfing 9000-9999€), ANO-002 (≥ 5 destinos/día),
      ANO-003 (≥ 5 orígenes en 6h al mismo destino), ANO-004 (recibir+enviar > 10k
      en < 60min), ANO-006 (mismo importe redondo ≥ 3x en 7 días), ANO-007
      (> 10k + CIERRE_CUENTA en < 48h), ANO-010 (microdeposit < 0,10€)
    - Comportamiento: ANO-025 (> 10 tx en 5 min), ANO-026 (cajero ciudad distinta
      < 90 min), ANO-027 (mismo cajero mismo importe 7 días seguidos), ANO-028
      (dos PAGO_COMERCIO terminales distintos < 2 min), ANO-029 (≥ 5 PAGO_ONLINE
      < 2 min), ANO-036 (brecha > 6h), ANO-037 (< 500ms entre ≥ 3 eventos/sesión)
    - Geopolítico/AML: ANO-042 (primer envío > 3000€ a destino desconocido),
      ANO-045 (≥ 10000€ DEPOSITO/RETIRO_CAJERO), ANO-047 (país embargado: RU/KP/IR/BY/SY/CU/VE/SD)
    - Cada alerta retorna `ANO-XXX|SEVERIDAD|CATEGORIA|detalle`
  - Nuevo `BankingLogController.java` — endpoints `/api/banking/transactions`,
    `/api/banking/batch` y `/api/banking/upload-csv` (con parser CSV línea a línea)
  - `AnomalyEntity.java` ampliado con campos `anoId`, `categoria` y `cuentaId`
  - `dashboard.html` actualizado: tabla de anomalías con columnas ANO-ID/Severidad/
    Categoría/Cuenta/Detalle; sección "Logs bancarios" con carga de CSV y tabla
    de resultados con código de color
  - `application.properties` con nuevas propiedades `auditpanel.banking.*`
- Carpeta `javi__panel__auditoria/` (original ZIP de Javi) eliminada por el coordinador
  una vez integrado el código limpio en `/panel`

### v0.3 — 25/05/2026 (Kike — coordinación)

- Integrado el panel de auditoría de Javi en `/panel` (rama `javier`)
- **Problema detectado:** Javi entregó su proyecto como ZIP con la carpeta
  `target/` de Maven incluida. Esa carpeta contiene el JAR compilado y todas
  las dependencias descargadas, lo que disparaba el peso a 89 MB.
  El código fuente real ocupa menos de 200 KB.
- **Causa:** El `.gitignore` original no excluía la carpeta `target/` de Maven.
- **Solución aplicada por Kike:** Se actualizó el `.gitignore` del proyecto
  añadiendo `target/`, rutas de IDEs (`.idea/`, `.vscode/`) y ficheros de
  credenciales (`*.p12`, `*.jks`, `.env`). Se copió únicamente el código
  fuente al módulo `/panel`, descartando todo compilado y binario.
- Se añadieron al proyecto compartido: `pom.xml` padre, `schema.sql`,
  `formato-logs.md` y `application.properties.template` como base común
  para todos los módulos.

### v0.2 — 25/05/2026 (Kike — coordinación)

- Añadido `pom.xml` padre con Spring Boot 3.2, JPA, H2/MySQL y Lombok
- Añadido `schema.sql` con tablas: clientes, cuentas, transacciones,
  eventos_sesion y anomalias_detectadas con índices
- Añadido `formato-logs.md` — contrato de datos entre generador y detector
- Añadido `application.properties.template` — configuración base común
- Actualizado `.gitignore` con entradas Maven, IDEs y secretos
- Ampliado `anomalias.md` al formato del Entregable 2 (~200 elementos
  organizados por categoría: diseño, implementación, configuración,
  autenticación, autorización, trazabilidad, despliegue, monitorización,
  mantenimiento, disponibilidad, protección de datos, fallos humanos
  y fallos legales/organizativos)

### v0.1 — 23/05/2026

- Creación del repositorio
- Configuración de rama principal protegida
- Añadidos colaboradores: Juan Carlos, Adrián, Javier
- Creadas ramas individuales de trabajo
- Estructura de carpetas definida: /generador, /detector, /panel
- README inicial con descripción del proyecto, equipo y normas
