import { useEffect, useState } from 'react'
import { BarChart, Bar, XAxis, YAxis, Tooltip, Legend, CartesianGrid, ResponsiveContainer, ComposedChart, Area, Line } from 'recharts'

const get = (u) => fetch(u).then((r) => r.json())
const SKUS = ['SKU-1001', 'SKU-1002', 'SKU-1003']

export default function App() {
  const [stock, setStock] = useState([])
  const [pos, setPos] = useState([])
  const [acc, setAcc] = useState([])
  const [sel, setSel] = useState(null)
  const [fc, setFc] = useState([])
  const [form, setForm] = useState({ sku: SKUS[0], warehouseId: 'WH-NORTH', type: 'SHIPPED', quantity: 50 })
  const [msg, setMsg] = useState('')

  useEffect(() => {
    const load = () => {
      get('/api/stock').then(setStock).catch(() => {})
      get('/api/purchase-orders').then(setPos).catch(() => {})
      get('/api/forecast-accuracy').then(setAcc).catch(() => {})
    }
    load()
    const t = setInterval(load, 3000)
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    if (sel) get(`/api/forecasts/${sel.sku}/${sel.warehouse_id}`).then(setFc).catch(() => setFc([]))
  }, [sel])

  const submit = async (e) => {
    e.preventDefault()
    const res = await fetch('/api/events', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...form, quantity: Number(form.quantity) }),
    })
    setMsg(res.ok ? `Recorded ${form.type.toLowerCase()} of ${form.quantity} for ${form.sku}` : 'Event rejected: check SKU and quantity')
  }

  const chartData = stock.map((s) => ({ name: `${s.sku} ${s.warehouse_id}`, onHand: s.on_hand, reorderPoint: s.reorder_point }))
  const accFor = (s) => acc.find((a) => a.sku === s.sku && a.warehouse_id === s.warehouse_id)

  return (
    <main>
      <h1>Inventory control</h1>
      <p className="sub">Live stock by warehouse. Updates within seconds of each recorded event.</p>

      <section className="panel">
        <h2>Stock levels</h2>
        {stock.length === 0 ? <p className="empty">No stock yet. Record an event below or run scripts/seed.py.</p> : (
          <div className="table-wrap"><table>
            <thead><tr><th>SKU</th><th>Item</th><th>Warehouse</th><th>On hand</th><th>Reorder at</th><th>Status</th><th>Forecast MAPE</th></tr></thead>
            <tbody>{stock.map((s) => {
              const low = s.on_hand < s.reorder_point
              const a = accFor(s)
              const picked = sel && sel.sku === s.sku && sel.warehouse_id === s.warehouse_id
              return (
                <tr key={s.sku + s.warehouse_id} className={`pick ${picked ? 'sel' : ''}`} tabIndex={0}
                    onClick={() => setSel(s)} onKeyDown={(e) => e.key === 'Enter' && setSel(s)}>
                  <td>{s.sku}</td><td>{s.name}</td><td>{s.warehouse_id}</td><td>{s.on_hand}</td><td>{s.reorder_point}</td>
                  <td className={low ? 'low' : 'ok'}>{low ? 'Reorder' : 'In stock'}</td>
                  <td>{a ? `${a.mape.toFixed(1)}%` : '-'}</td>
                </tr>)
            })}</tbody>
          </table></div>)}
      </section>

      <div className="grid">
        <section className="panel">
          <h2>On hand vs reorder point</h2>
          <div style={{ height: 260 }}><ResponsiveContainer>
            <BarChart data={chartData}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="name" hide /><YAxis /><Tooltip /><Legend />
              <Bar dataKey="onHand" name="On hand" fill="#1c5d99" /><Bar dataKey="reorderPoint" name="Reorder point" fill="#d9480f" /></BarChart>
          </ResponsiveContainer></div>
        </section>

        <section className="panel">
          <h2>{sel ? `Demand forecast: ${sel.sku}, ${sel.warehouse_id}` : 'Demand forecast'}</h2>
          {!sel ? <p className="empty">Select a row to see its 14-day forecast.</p>
            : fc.length === 0 ? <p className="empty">No forecast yet. The forecaster needs 30+ days of shipment history.</p> : (
            <div style={{ height: 260 }}><ResponsiveContainer>
              <ComposedChart data={fc}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="ds" /><YAxis /><Tooltip />
                <Area dataKey="yhat_upper" stroke="none" fill="#c8d9ea" name="Upper" /><Area dataKey="yhat_lower" stroke="none" fill="#fff" name="Lower" />
                <Line dataKey="yhat" stroke="#1c5d99" dot={false} name="Units per day" /></ComposedChart>
            </ResponsiveContainer></div>)}
        </section>
      </div>

      <section className="panel">
        <h2>Replenishment orders</h2>
        {pos.length === 0 ? <p className="empty">No orders yet. One is created automatically when stock drops below its reorder point.</p> : (
          <div className="table-wrap"><table>
            <thead><tr><th>Order</th><th>SKU</th><th>Warehouse</th><th>Quantity</th><th>Status</th><th>SAP ref</th></tr></thead>
            <tbody>{pos.map((p) => <tr key={p.id}><td>#{p.id}</td><td>{p.sku}</td><td>{p.warehouse_id}</td><td>{p.quantity}</td><td>{p.status}</td><td>{p.sap_ref || '-'}</td></tr>)}</tbody>
          </table></div>)}
      </section>

      <section className="panel">
        <h2>Record inventory event</h2>
        <form onSubmit={submit}>
          <label>SKU<select value={form.sku} onChange={(e) => setForm({ ...form, sku: e.target.value })}>{SKUS.map((s) => <option key={s}>{s}</option>)}</select></label>
          <label>Warehouse<select value={form.warehouseId} onChange={(e) => setForm({ ...form, warehouseId: e.target.value })}><option>WH-NORTH</option><option>WH-SOUTH</option></select></label>
          <label>Type<select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}><option>RECEIVED</option><option>SHIPPED</option><option>ADJUSTED</option></select></label>
          <label>Quantity<input type="number" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} /></label>
          <button type="submit">Record event</button>
        </form>
        {msg && <p className="sub" role="status">{msg}</p>}
      </section>
    </main>
  )
}
