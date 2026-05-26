# Panel de Auditoría - Extensiones

Este proyecto incluye ahora ingestión masiva por API, carga de archivos y publicación opcional a Kafka.

## Archivos de interfaz

- `src/main/resources/static/dashboard.html`
- `src/main/resources/static/dashboard.js`
- `src/main/resources/static/dashboard.css`

Abre en el navegador:

```text
http://localhost:8080/dashboard.html
```

## Nuevos endpoints

- `POST /api/logs/upload`: carga un archivo CSV o JSON.
- `POST /api/logs/batch`: ingiere múltiples eventos.
- `GET /api/logs/recent?source=...&level=...&minutes=...&contains=...`: consulta filtrada.
- `GET /api/logs/summary`: resumen de métricas, top fuentes, top niveles y anomalías.

## Elasticsearch / Kibana

Si habilitas Elasticsearch, los logs se indexarán en el índice configurado y podrás consultar resultados desde Kibana.

Para habilitarlo, edita `src/main/resources/application.properties`:

```properties
auditpanel.elasticsearch.enabled=true
spring.elasticsearch.rest.uris=http://localhost:9200
```

## Persistencia SQL opcional

La persistencia SQL con PostgreSQL se configura en `src/main/resources/application-sql.properties`.

Activa el perfil `sql` para usarla:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=sql
```

Asegúrate de tener PostgreSQL en el puerto configurado y la base de datos creada.

> Alternativamente puedes levantar Elasticsearch y Kibana con Docker Compose:
>
> ```bash
docker-compose up -d
> ```
> 
> Esto habilita un stack local compatible con `spring-boot-starter-data-elasticsearch`.

## Soporte de archivos

- JSON: un solo objeto o un arreglo de objetos `LogEvent`.
- CSV: columnas `id,timestamp,source,level,message` con encabezado.

## Kafka opcional

Para habilitar Kafka, activa en `src/main/resources/application.properties`:

```properties
auditpanel.kafka.enabled=true
spring.kafka.bootstrap-servers=localhost:9092
```

Luego reinicia la aplicación.

## Ejemplo de carga de archivo

```bash
curl -X POST http://localhost:8080/api/logs/upload \
  -F "file=@logs.csv"
```

## Ejemplo de ingestión masiva JSON

```bash
curl -X POST http://localhost:8080/api/logs/batch \
  -H "Content-Type: application/json" \
  -d '[
    {"source":"payment-service","level":"ERROR","message":"Timeout al procesar pago"},
    {"source":"auth-service","level":"WARN","message":"Intento de login fallido"}
  ]'
```
