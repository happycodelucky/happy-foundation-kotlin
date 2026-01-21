package com.happycodelucky.serializers

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Serializer for [LocalDateTime]
 */
internal object LocalDateTimeISO8601Serializer : KSerializer<LocalDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            serialName = "LocalDateTime",
            kind = PrimitiveKind.STRING,
        )

    override fun serialize(
        encoder: Encoder,
        value: LocalDateTime,
    ) {
        encoder.encodeString(value.toInstant(TimeZone.UTC).toString())
    }

    override fun deserialize(decoder: Decoder): LocalDateTime {
        val decodedString = decoder.decodeString()
        return Instant.parse(decodedString).toLocalDateTime(TimeZone.currentSystemDefault())
    }
}

typealias SerializableLocalDateTime =
    @Serializable(LocalDateTimeISO8601Serializer::class)
    LocalDateTime
