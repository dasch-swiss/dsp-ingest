# Monitoring Endpoints

These endpoints are used to operate the service and provide information about the service.
These endpoints are not secured.

## Health Endpoint 

Provides a health check endpoint for the service.
The endpoint returns a 200 OK if the service is healthy.

Request:

```http
GET /health
```

Response:

```http
HTTP/1.1 200 OK
content-type: application/json

{
  "status": "UP"
}
```

## Metrics Endpoint

Provides a metrics endpoint for the service.

```http
GET /metrics
```

Metrics are exposed as content type `text/plain` in the [Prometheus](https://prometheus.io/docs/introduction/overview/)
format.

## Info Endpoint

The info endpoint provides information about the service artefact.

```http
GET /info
```

Example response:

```http
HTTP/1.1 200 OK
content-type: application/json

{
    "buildTime": "2023-06-29 17:21:43.081+0200",
    "gitCommit": "ae1246a98819389e0d54c36032f63ef2802e49a8",
    "name": "dsp-ingest",
    "sbtVersion": "1.9.0",
    "scalaVersion": "3.3.0",
    "version": "0.0.1-14-gae1246a"
}
```