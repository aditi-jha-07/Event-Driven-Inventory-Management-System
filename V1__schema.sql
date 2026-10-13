create table products (
  sku           text primary key,
  name          text not null,
  reorder_point int  not null,
  reorder_qty   int  not null
);

-- Read model (CQRS query side), projected from the stock-levels topic
create table stock_levels (
  sku          text not null references products(sku),
  warehouse_id text not null,
  on_hand      int  not null,
  updated_at   timestamptz not null,
  primary key (sku, warehouse_id)
);

create table purchase_orders (
  id           bigserial primary key,
  sku          text not null references products(sku),
  warehouse_id text not null,
  quantity     int  not null,
  status       text not null default 'CREATED',  -- CREATED -> SENT_TO_SAP -> FULFILLED
  sap_ref      text,
  created_at   timestamptz not null default now()
);

create table forecasts (
  sku          text not null,
  warehouse_id text not null,
  ds           date not null,
  yhat         double precision not null,
  yhat_lower   double precision,
  yhat_upper   double precision,
  primary key (sku, warehouse_id, ds)
);

create table forecast_accuracy (
  sku          text not null,
  warehouse_id text not null,
  mape         double precision not null,
  computed_at  timestamptz not null default now(),
  primary key (sku, warehouse_id)
);

insert into products values
  ('SKU-1001', 'Pallet wrap',       100, 500),
  ('SKU-1002', 'Cardboard cartons', 150, 400),
  ('SKU-1003', 'Shrink film',        80, 300);
