package com.example.inventory

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * Pushes purchase orders to SAP over REST. The endpoint and payload below are a placeholder
 * contract: adapt them to your SAP gateway (OData / API Management).
 */
@Component
class SapClient(@Value("\${sap.base-url:}") private val baseUrl: String) {

    fun submit(poId: Long, s: ReorderSuggestion): String {
        if (baseUrl.isBlank()) return "SIM-$poId"
        val res = RestClient.create(baseUrl).post()
            .uri("/purchase-orders")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("externalId" to poId, "material" to s.sku, "plant" to s.warehouseId, "quantity" to s.suggestedQty))
            .retrieve()
            .body(Map::class.java)
        return res?.get("documentNumber")?.toString() ?: "UNKNOWN"
    }
}
