package com.happycodelucky.serialization

import kotlinx.collections.immutable.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class ImmutableListSerializer<T>(private val tSerializer: KSerializer<T>) : KSerializer<ImmutableList<T>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor(
        serialName = "kotlinx.collections.immutable.ImmutableList",
        typeParameters = arrayOf(tSerializer.descriptor),
    )

    override fun serialize(encoder: Encoder, value: ImmutableList<T>) {
        return ListSerializer(tSerializer).serialize(encoder, value)
    }
    override fun deserialize(decoder: Decoder): ImmutableList<T> {
        return ListSerializer(tSerializer).deserialize(decoder).toPersistentList()
    }
}

typealias SerializableImmutableList<T> = @Serializable(ImmutableListSerializer::class) ImmutableList<T>
