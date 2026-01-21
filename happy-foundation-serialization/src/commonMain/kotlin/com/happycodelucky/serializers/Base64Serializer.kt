package com.happycodelucky.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64

/**
 * Serializer for Base64 encoded strings
 */
internal object Base64Serializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Base64", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: String,
    ) {
        encoder.encodeString(Base64.encode(value.encodeToByteArray()))
    }

    override fun deserialize(decoder: Decoder): String = Base64.decode(decoder.decodeString() as CharSequence).decodeToString()
}

typealias SerializableBase64String =
    @Serializable(Base64Serializer::class)
    String
