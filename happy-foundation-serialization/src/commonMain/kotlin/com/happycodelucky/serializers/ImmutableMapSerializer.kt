package com.happycodelucky.serializers

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toPersistentHashMap
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class ImmutableMapSerializer<K, V>(
    private val kSerializer: KSerializer<K>,
    private val vSerializer: KSerializer<V>,
) : KSerializer<ImmutableMap<K, V>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor(
            serialName = "kotlinx.collections.immutable.ImmutableMap",
            typeParameters = arrayOf(kSerializer.descriptor, vSerializer.descriptor),
        )

    override fun serialize(
        encoder: Encoder,
        value: ImmutableMap<K, V>,
    ) = MapSerializer(kSerializer, vSerializer).serialize(encoder, value)

    override fun deserialize(decoder: Decoder): ImmutableMap<K, V> =
        MapSerializer(kSerializer, vSerializer).deserialize(decoder).toPersistentHashMap()
}

typealias SerializableImmutableMap<K, V> =
    @Serializable(ImmutableMapSerializer::class)
    ImmutableMap<K, V>
