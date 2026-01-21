package com.happycodelucky.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializer for Hex Colors encoded strings - #RRGGBB
 *
 * TODO: Support ARGB/RGBA
 */
internal object HexColorRGBSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("HexColorRGB", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeString("#${value.toString(16).padStart(6, '0')}")
    }

    override fun deserialize(decoder: Decoder): Long {
        var hex = decoder.decodeString().trimStart('#')
        if (hex.length == 2) {
            hex = hex.repeat(3)
        } else if (hex.length > 6) {
            hex = hex.substring(hex.length - 6)
        }
        return hex.toLong(16)
    }
}

typealias SerializableHexColorRGB = @Serializable(HexColorRGBSerializer::class) Long

