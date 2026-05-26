# Panel de Auditoría - Detección de Anomalías en Logs

Proyecto Spring Boot que ingiere eventos de log, detecta anomalías básicas y muestra un panel web.

## Ejecutar

1. Abre el proyecto en VS Code.
2. Si tu terminal no tiene `JAVA_HOME`, ejecuta el script PowerShell:

```powershell
.\set-java-home.ps1
```

3. Inicia el proyecto con Maven o usando la tarea de VS Code:

```bash
mvn clean spring-boot:run
```

4. Si quieres habilitar la persistencia SQL en PostgreSQL, activa el perfil `sql`:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=sql
```

4. Abre en el navegador:

```text
http://localhost:8080
```

5. Para usar el dashboard extendido:

```text
http://localhost:8080/dashboard.html
```

6. Para levantar Elasticsearch y Kibana localmente, usa `docker-compose`:

```bash
docker-compose up -d
```

> También puedes usar la tarea de VS Code `Run Spring Boot` desde el panel de tareas.

## Endpoints

### Logs genéricos

- `POST /api/logs`: ingresa un evento JSON.
- `POST /api/logs/batch`: ingiere múltiples eventos en una sola llamada.
- `GET /api/logs/summary`: obtiene métricas, niveles, fuentes, anomalías y últimos logs.
- `GET /api/logs/recent`: lista eventos recientes, con filtros por `source`, `level`, `minutes` y `contains`.

### Logs bancarios (formato `formato-logs.md`)

- `POST /api/banking/transactions`: procesa una transacción bancaria en formato pipe-separated.
- `POST /api/banking/batch`: procesa un array JSON de transacciones bancarias.
- `POST /api/banking/upload-csv`: sube un fichero CSV con transacciones (parser línea a línea).

## Ejemplo de evento JSON

```json
{
  "source": "payment-service",
  "level": "ERROR",
  "message": "Timeout al procesar pago",
  "metadata": {
    "orderId": "12345"
  }
}
```

## Ejemplo de ingestión masiva

```bash
curl -X POST http://localhost:8080/api/logs/batch \
  -H "Content-Type: application/json" \
  -d '[
    {"source":"payment-service","level":"ERROR","message":"Timeout al procesar pago"},
    {"source":"auth-service","level":"WARN","message":"Intento de login fallido"}
  ]'
```

## Ejemplo de filtro de logs

```text
GET /api/logs/recent?source=payment-service&level=ERROR&minutes=15&contains=timeout
```

## Qué incluye

- Backend Spring Boot con ingesta REST.
- Detector de anomalías basado en tasa de errores y patrones de mensajes.
- Panel estático con resumen en tiempo real.
