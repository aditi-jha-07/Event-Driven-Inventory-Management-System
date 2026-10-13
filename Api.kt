package com.example.inventory

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class EventRequest(
    val sku: String,
    val warehouseId: String,
    val type: EventType,
    val quantity: Int,
    val timestamp: Instant? = null,
)

@RestController
@RequestMapping("/api")
class Api(
    private val kafka: KafkaTemplate<String, String>,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val products: ProductRepo,
) {
    // Command side: validate, append to the log, return. State changes happen downstream.
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun publish(@RequestBody r: EventRequest): Map<String, String> {
        if (r.quantity == 0 || (r.type != EventType.ADJUSTED && r.quantity < 0))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be positive (ADJUSTED may be negative)")
        if (products.find(r.sku) == null)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown sku ${r.sku}")
        val e = InventoryEvent(sku = r.sku, warehouseId = r.warehouseId, type = r.type,
            quantity = r.quantity, timestamp = r.timestamp ?: Instant.now())
        kafka.send(Topics.EVENTS, "${e.sku}|${e.warehouseId}", mapper.writeValueAsString(e))
        return mapOf("eventId" to e.eventId)
    }

    // Query side
    @GetMapping("/stock")
    fun stock(): List<Map<String, Any>> = jdbc.queryForList(
        """select s.sku, p.name, s.warehouse_id, s.on_hand, p.reorder_point, s.updated_at
           from stock_levels s join products p using (sku) order by s.sku, s.warehouse_id""")

    @GetMapping("/purchase-orders")
    fun purchaseOrders(): List<Map<String, Any>> =
        jdbc.queryForList("select * from purchase_orders order by created_at desc limit 50")

    @GetMapping("/forecasts/{sku}/{warehouse}")
    fun forecast(@PathVariable sku: String, @PathVariable warehouse: String): List<Map<String, Any>> =
        jdbc.queryForList("select ds, yhat, yhat_lower, yhat_upper from forecasts where sku = ? and warehouse_id = ? order by ds", sku, warehouse)

    @GetMapping("/forecast-accuracy")
    fun accuracy(): List<Map<String, Any>> =
        jdbc.queryForList("select sku, warehouse_id, mape, computed_at from forecast_accuracy order by sku, warehouse_id")
}
