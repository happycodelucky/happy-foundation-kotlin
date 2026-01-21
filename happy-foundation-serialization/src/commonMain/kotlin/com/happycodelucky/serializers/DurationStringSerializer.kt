package com.happycodelucky.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Duration

/**
 * Duration serializer
 */
internal object DurationStringSerializer : KSerializer<Duration> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DurationString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Duration,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Duration = Duration.parseOrNull(decoder.decodeString()) ?: Duration.ZERO
}

typealias SerializableDuration =
    @Serializable(DurationStringSerializer::class)
    Duration
