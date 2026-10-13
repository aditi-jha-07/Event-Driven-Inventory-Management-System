package com.example.inventory

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.common.utils.Bytes
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.kstream.Aggregator
import org.apache.kafka.streams.kstream.Consumed
import org.apache.kafka.streams.kstream.Grouped
import org.apache.kafka.streams.kstream.Initializer
import org.apache.kafka.streams.kstream.KStream
import org.apache.kafka.streams.kstream.Materialized
import org.apache.kafka.streams.kstream.Produced
import org.apache.kafka.streams.state.KeyValueStore
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaStreams
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.support.serializer.JsonSerde
import kotlin.math.ceil

object Topics {
    const val EVENTS = "inventory-events"
    const val STOCK = "stock-levels"
    const val SUGGESTIONS = "reorder-suggestions"
}

@Configuration
@EnableKafkaStreams
class StreamsTopology(private val products: ProductRepo, private val mapper: ObjectMapper) {

    // Retain events forever: the log is the system of record.
    @Bean fun eventsTopic(): NewTopic =
        TopicBuilder.name(Topics.EVENTS).partitions(3).replicas(1)
            .config(TopicConfig.RETENTION_MS_CONFIG, "-1").build()

    @Bean fun stockTopic(): NewTopic = TopicBuilder.name(Topics.STOCK).partitions(3).replicas(1).build()
    @Bean fun suggestionsTopic(): NewTopic = TopicBuilder.name(Topics.SUGGESTIONS).partitions(3).replicas(1).build()

    private fun <T> serde(type: Class<T>) =
        JsonSerde(type, mapper).noTypeInfo().ignoreTypeHeaders()

    @Bean
    fun stockTopology(builder: StreamsBuilder): KStream<String, StockLevel> {
        val eventSerde = serde(InventoryEvent::class.java)
        val stockSerde = serde(StockLevel::class.java)
        val suggestionSerde = serde(ReorderSuggestion::class.java)

        // Fold events (keyed sku|warehouse) into current stock — the real-time inventory view.
        val levels: KStream<String, StockLevel> = builder
            .stream(Topics.EVENTS, Consumed.with(Serdes.String(), eventSerde))
            .groupByKey(Grouped.with(Serdes.String(), eventSerde))
            .aggregate(
                Initializer { StockLevel(sku = "", warehouseId = "") },
                Aggregator<String, InventoryEvent, StockLevel> { _, event, agg -> agg.applyEvent(event) },
                Materialized.`as`<String, StockLevel, KeyValueStore<Bytes, ByteArray>>("stock-levels-store")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(stockSerde),
            )
            .toStream()

        levels.to(Topics.STOCK, Produced.with(Serdes.String(), stockSerde))

        // Below reorder point -> emit a suggestion, sized by the forecast when one exists.
        levels.flatMapValues { s ->
            val p = products.find(s.sku)
            if (p == null || s.onHand >= p.reorderPoint) emptyList()
            else {
                val forecast = products.forecast7d(s.sku, s.warehouseId)
                val qty = maxOf(p.reorderQty, forecast?.let { ceil(it).toInt() - s.onHand } ?: 0)
                listOf(ReorderSuggestion(s.sku, s.warehouseId, s.onHand, p.reorderPoint, qty, forecast))
            }
        }.to(Topics.SUGGESTIONS, Produced.with(Serdes.String(), suggestionSerde))

        return levels
    }
}
