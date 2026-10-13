package com.example.inventory

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.sql.Timestamp

/** CQRS read side: builds Postgres tables from the stream output; creates and forwards POs. */
@Component
class Projection(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val products: ProductRepo,
    private val sap: SapClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = [Topics.STOCK], groupId = "stock-projection")
    fun onStock(payload: String) {
        val s = mapper.readValue<StockLevel>(payload)
        jdbc.update(
            """insert into stock_levels(sku, warehouse_id, on_hand, updated_at) values (?,?,?,?)
               on conflict (sku, warehouse_id) do update
               set on_hand = excluded.on_hand, updated_at = excluded.updated_at""",
            s.sku, s.warehouseId, s.onHand, Timestamp.from(s.updatedAt),
        )
        // Stock recovered above the reorder point: close any open PO.
        val p = products.find(s.sku)
        if (p != null && s.onHand >= p.reorderPoint) {
            jdbc.update(
                "update purchase_orders set status = 'FULFILLED' where sku = ? and warehouse_id = ? and status <> 'FULFILLED'",
                s.sku, s.warehouseId,
            )
        }
    }

    @KafkaListener(topics = [Topics.SUGGESTIONS], groupId = "po-generator")
    fun onSuggestion(payload: String) {
        val s = mapper.readValue<ReorderSuggestion>(payload)
        val open = jdbc.queryForObject(
            "select count(*) from purchase_orders where sku = ? and warehouse_id = ? and status <> 'FULFILLED'",
            Long::class.javaObjectType, s.sku, s.warehouseId,
        ) ?: 0L
        if (open > 0) return  // one open PO per sku/warehouse at a time

        val id = jdbc.queryForObject(
            "insert into purchase_orders(sku, warehouse_id, quantity) values (?,?,?) returning id",
            Long::class.javaObjectType, s.sku, s.warehouseId, s.suggestedQty,
        )!!
        try {
            val ref = sap.submit(id, s)
            jdbc.update("update purchase_orders set status = 'SENT_TO_SAP', sap_ref = ? where id = ?", ref, id)
        } catch (e: Exception) {
            log.warn("SAP submit failed for PO {}: {}", id, e.message)  // stays CREATED
        }
    }
}
