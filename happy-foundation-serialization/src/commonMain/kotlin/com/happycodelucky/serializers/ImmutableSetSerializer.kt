package com.happycodelucky.serializers

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toPersistentHashSet
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class ImmutableSetSerializer<T>(private val tSerializer: KSerializer<T>) :
    KSerializer<ImmutableSet<T>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
        serialName = "kotlinx.collections.immutable.ImmutableSet",
        typeParameters = arrayOf(tSerializer.descriptor),
    )

    override fun serialize(encoder: Encoder, value: ImmutableSet<T>) {
        return SetSerializer(tSerializer).serialize(encoder, value)
    }
    override fun deserialize(decoder: Decoder): ImmutableSet<T> {
        return SetSerializer(tSerializer).deserialize(decoder).toPersistentHashSet()
    }
}

typealias SerializableImmutableSet<T> = @Serializable(ImmutableSetSerializer::class) ImmutableSet<T>
