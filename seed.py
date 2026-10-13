"""Post ~120 days of synthetic, backdated events through the API so the forecaster has history.
Ends with stock below the reorder point so a purchase order is generated."""
import json, math, random, urllib.request
from datetime import datetime, timedelta, timezone

API = "http://localhost:8080/api/events"
random.seed(7)
SKUS = {"SKU-1001": 22, "SKU-1002": 30, "SKU-1003": 15}   # average daily demand
WAREHOUSES = ["WH-NORTH", "WH-SOUTH"]
DAYS = 120

def post(sku, wh, typ, qty, ts):
    body = json.dumps({"sku": sku, "warehouseId": wh, "type": typ, "quantity": qty,
                       "timestamp": ts.isoformat().replace("+00:00", "Z")}).encode()
    req = urllib.request.Request(API, body, {"Content-Type": "application/json"})
    urllib.request.urlopen(req).read()

start = datetime.now(timezone.utc).replace(hour=9, minute=0, second=0, microsecond=0) - timedelta(days=DAYS)
for sku, base in SKUS.items():
    for wh in WAREHOUSES:
        stock = 800
        post(sku, wh, "RECEIVED", stock, start)
        for d in range(1, DAYS):
            ts = start + timedelta(days=d)
            weekly = 1 + 0.35 * math.sin(2 * math.pi * ts.weekday() / 7)
            demand = max(1, int(random.gauss(base * weekly, base * 0.15)))
            demand = min(demand, stock)
            post(sku, wh, "SHIPPED", demand, ts); stock -= demand
            if stock < 250:
                post(sku, wh, "RECEIVED", 600, ts + timedelta(hours=2)); stock += 600
        if stock > 40:                      # push below the reorder point
            post(sku, wh, "SHIPPED", stock - 40, datetime.now(timezone.utc))
        print("seeded", sku, wh)
