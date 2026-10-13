package com.example.inventory

import java.time.Instant
import java.util.UUID

enum class EventType { RECEIVED, SHIPPED, ADJUSTED }

/** Immutable fact. The inventory-events topic is the source of truth. */
data class InventoryEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val sku: String,
    val warehouseId: String,
    val type: EventType,
    val quantity: Int,            // always positive, except ADJUSTED which is signed
    val timestamp: Instant = Instant.now(),
)

data class StockLevel(
    val sku: String,
    val warehouseId: String,
    val onHand: Int = 0,
    val updatedAt: Instant = Instant.now(),
)

data class ReorderSuggestion(
    val sku: String,
    val warehouseId: String,
    val onHand: Int,
    val reorderPoint: Int,
    val suggestedQty: Int,
    val forecastDemand7d: Double?,
)

data class Product(val sku: String, val name: String, val reorderPoint: Int, val reorderQty: Int)

fun InventoryEvent.delta(): Int = when (type) {
    EventType.RECEIVED -> quantity
    EventType.SHIPPED -> -quantity
    EventType.ADJUSTED -> quantity
}

fun StockLevel.applyEvent(e: InventoryEvent) = copy(
    sku = e.sku,
    warehouseId = e.warehouseId,
    onHand = maxOf(0, onHand + e.delta()),
    updatedAt = e.timestamp,
)
