# Event-Driven-Inventory-Management-System
Implemented event sourcing with Kafka Streams to maintain a real‑time inventory view. A React dashboard triggers replenishment suggestions based on demand forecasting models.
Event‑Driven Inventory Management System
====================================

NDA Statement: Logistics company and cost savings figures are generalised. The event sourcing architecture is genuine.

Client – A logistics company (anonymised)
Role – Full‑Stack Developer
Duration – 7 months

Problem Statement
Legacy batch‑based inventory updates caused stockouts and over‑ordering across warehouses.

Solution
Implemented event sourcing with Kafka Streams to maintain a real‑time inventory view. A React dashboard triggers replenishment suggestions based on demand forecasting models.

Key Features

Inventory events as source of truth (CQRS)

Real‑time stock levels and movement tracking

Automated purchase order generation when below reorder point

Integration with ERP (SAP) via REST APIs

Forecast accuracy dashboard using Prophet

Tech Stack

Backend: Kotlin (Spring Boot), Kafka Streams, PostgreSQL

Frontend: React, Recharts

Forecasting: Python (Prophet), scheduled jobs

DevOps: Helm charts, ArgoCD, Grafana Loki

Outcome

Stockout incidents reduced by 60%

Inventory carrying costs lowered by 18%
