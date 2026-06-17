package com.example.aitodoapp.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString(), DateTimeFormatter.ISO_LOCAL_DATE)
    }
}

object LocalDateListSerializer : KSerializer<List<LocalDate>> {
    private val delegate = ListSerializer(LocalDateSerializer)
    override val descriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: List<LocalDate>) = delegate.serialize(encoder, value)
    override fun deserialize(decoder: Decoder): List<LocalDate> = delegate.deserialize(decoder)
}
