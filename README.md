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

## Run it

```bash
docker compose up --build          # Kafka (KRaft), Postgres, backend, forecaster, dashboard
python3 scripts/seed.py            # 120 days of synthetic events, ends below reorder points
open http://localhost:3000
```

Within a few seconds the dashboard shows stock below reorder points and purchase orders
(`SIM-<id>` refs, since no SAP URL is set). The forecaster runs every 5 minutes; once it has
written results, select a row to see the 14-day forecast and MAPE.

Set `SAP_BASE_URL` to enable real calls. The payload in `SapClient.kt` is a placeholder contract.

## Design notes

- Replay: the stock view is a pure fold over events, so you can rebuild it by resetting the
  streams application id.
- Duplicate safety: the topology is at-least-once; projections are idempotent upserts and
  PO creation is guarded to one open PO per sku/warehouse.
- Grafana Loki: log shipping (Promtail/Alloy) is cluster-level and not included in the chart.
  The backend logs to stdout, which Loki picks up.

## Known gaps

- Not compiled or run in the authoring environment (no Maven/npm access). Expect to fix small
  build issues on first run.
- No auth, no tests, and the Helm chart assumes Kafka and Postgres already exist in the cluster.
