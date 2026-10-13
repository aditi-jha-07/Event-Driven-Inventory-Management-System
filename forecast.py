"""Demand forecasting job: replays inventory-events, fits Prophet per sku/warehouse,
writes 14-day forecasts and a holdout MAPE to Postgres. Runs on a loop locally and as a
Kubernetes CronJob in the cluster (set RUN_ONCE=true)."""
import json, logging, os, time
from collections import defaultdict

import numpy as np
import pandas as pd
import psycopg2
from confluent_kafka import Consumer
from prophet import Prophet

logging.getLogger("cmdstanpy").setLevel(logging.WARNING)
logging.getLogger("prophet").setLevel(logging.WARNING)

BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP", "localhost:29092")
TOPIC = os.getenv("EVENTS_TOPIC", "inventory-events")
DSN = os.getenv("DB_DSN", "postgresql://inventory:inventory@localhost:5432/inventory")
INTERVAL = int(os.getenv("INTERVAL_SECONDS", "3600"))
RUN_ONCE = os.getenv("RUN_ONCE", "false").lower() == "true"
HORIZON, HOLDOUT, MIN_DAYS = 14, 14, 30


def load_daily_demand():
    """Sum SHIPPED quantity per (sku, warehouse, day) by reading the log from the start."""
    c = Consumer({
        "bootstrap.servers": BOOTSTRAP,
        "group.id": f"forecaster-{time.time_ns()}",
        "auto.offset.reset": "earliest",
        "enable.auto.commit": False,
    })
    c.subscribe([TOPIC])
    daily, idle = defaultdict(float), 0
    while idle < 5:
        msg = c.poll(1.0)
        if msg is None:
            idle += 1
            continue
        if msg.error():
            continue
        idle = 0
        e = json.loads(msg.value())
        if e["type"] == "SHIPPED":
            daily[(e["sku"], e["warehouseId"], e["timestamp"][:10])] += e["quantity"]
    c.close()
    return daily


def series(rows):
    df = pd.DataFrame(rows, columns=["ds", "y"])
    df["ds"] = pd.to_datetime(df["ds"])
    full = pd.date_range(df.ds.min(), df.ds.max(), freq="D")
    return df.groupby("ds").y.sum().reindex(full, fill_value=0).rename_axis("ds").reset_index()


def fit(df):
    return Prophet(weekly_seasonality=True, daily_seasonality=False).fit(df)


def mape(df):
    train, test = df.iloc[:-HOLDOUT], df.iloc[-HOLDOUT:]
    pred = fit(train).predict(test[["ds"]]).yhat.values
    y = test.y.values
    mask = y > 0
    return float(np.mean(np.abs(y[mask] - pred[mask]) / y[mask]) * 100) if mask.any() else None


def run():
    daily = load_daily_demand()
    grouped = defaultdict(list)
    for (sku, wh, day), qty in daily.items():
        grouped[(sku, wh)].append((day, qty))

    with psycopg2.connect(DSN) as conn, conn.cursor() as cur:
        for (sku, wh), rows in grouped.items():
            df = series(rows)
            if len(df) < MIN_DAYS:
                logging.warning("skip %s/%s: %d days of history", sku, wh, len(df))
                continue
            model = fit(df)
            fc = model.predict(model.make_future_dataframe(periods=HORIZON)).tail(HORIZON)
            cur.execute("delete from forecasts where sku=%s and warehouse_id=%s", (sku, wh))
            for r in fc.itertuples():
                cur.execute(
                    "insert into forecasts(sku, warehouse_id, ds, yhat, yhat_lower, yhat_upper) values (%s,%s,%s,%s,%s,%s)",
                    (sku, wh, r.ds.date(), max(0.0, r.yhat), max(0.0, r.yhat_lower), max(0.0, r.yhat_upper)),
                )
            err = mape(df) if len(df) >= MIN_DAYS + HOLDOUT else None
            if err is not None:
                cur.execute(
                    """insert into forecast_accuracy(sku, warehouse_id, mape) values (%s,%s,%s)
                       on conflict (sku, warehouse_id) do update set mape=excluded.mape, computed_at=now()""",
                    (sku, wh, err),
                )
            logging.warning("forecast %s/%s done (MAPE=%s)", sku, wh, None if err is None else round(err, 1))


if __name__ == "__main__":
    while True:
        try:
            run()
        except Exception as ex:  # tables may not exist yet on first boot
            logging.warning("forecast run failed: %s", ex)
        if RUN_ONCE:
            break
        time.sleep(INTERVAL)
