# Event-Driven Inventory Management System

Event-sourced inventory with Kafka Streams, a React dashboard, and Prophet demand forecasting.

> NDA note: the original client (a logistics company) is anonymised. This repository is a
> reference implementation of the architecture, not client code. The outcome figures from the
> engagement (60% fewer stockouts, 18% lower carrying costs) are not produced by this repo
> and should be described as results of the client project only.

## Architecture

```
POST /api/events ──▶ [inventory-events]  (infinite retention, source of truth)
                          │ Kafka Streams: groupByKey(sku|warehouse).aggregate
                          ▼
                    [stock-levels] ─────────▶ Postgres stock_levels  (CQRS read model)
                          │ below reorder point?
                          ▼
                 [reorder-suggestions] ──▶ purchase_orders ──▶ SAP (REST)
Prophet job ◀── replays inventory-events ──▶ forecasts + forecast_accuracy (MAPE)
React dashboard ◀── /api/stock, /purchase-orders, /forecasts, /forecast-accuracy
```

| Layer | Stack |
|---|---|
| Backend | Kotlin, Spring Boot 3, Kafka Streams, PostgreSQL, Flyway |
| Frontend | React (Vite), Recharts |
| Forecasting | Python, Prophet, scheduled job (loop locally, CronJob in cluster) |
| DevOps | Docker Compose, Helm chart, ArgoCD Application |

## Design notes

- Replay: the stock view is a pure fold over events, so you can rebuild it by resetting the
  streams application id.
- Duplicate safety: the topology is at-least-once; projections are idempotent upserts and
  PO creation is guarded to one open PO per sku/warehouse.
- Grafana Loki: log shipping (Promtail/Alloy) is cluster-level and not included in the chart.
  The backend logs to stdout, which Loki picks up.
