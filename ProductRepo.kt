package com.example.inventory

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ProductRepo(private val jdbc: JdbcTemplate) {

    fun find(sku: String): Product? = jdbc.query(
        "select sku, name, reorder_point, reorder_qty from products where sku = ?",
        { rs, _ -> Product(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4)) },
        sku,
    ).firstOrNull()

    /** Sum of the next 7 forecast days written by the Prophet job, or null if none yet. */
    fun forecast7d(sku: String, warehouseId: String): Double? {
        val rows = jdbc.queryForList(
            "select yhat from forecasts where sku = ? and warehouse_id = ? and ds > current_date order by ds limit 7",
            Double::class.javaObjectType, sku, warehouseId,
        )
        return if (rows.isEmpty()) null else rows.sumOf { it.toDouble() }
    }
}
