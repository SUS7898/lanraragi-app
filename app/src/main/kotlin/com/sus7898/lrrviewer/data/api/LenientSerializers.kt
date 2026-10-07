package com.sus7898.lrrviewer.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/*
 * LANraragi (and especially older versions / plugins) are not strict about JSON types:
 * numbers may arrive as strings, empty strings, or null, and booleans as "true"/"none"/1.
 * The Mihon extension crashes with `NumberFormatException: For input string: ""` on such
 * data; these serializers make every numeric/boolean field tolerant instead.
 */

private fun Decoder.primitiveOrNull(): JsonPrimitive? {
    val jd = this as? JsonDecoder ?: return null
    val el = jd.decodeJsonElement()
    return el as? JsonPrimitive
}

object LenientIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        val p = decoder.primitiveOrNull() ?: return 0
        if (p is JsonNull) return 0
        val s = p.content.trim()
        return s.toIntOrNull() ?: s.toDoubleOrNull()?.toInt() ?: 0
    }
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

object LenientLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long {
        if (decoder !is JsonDecoder) return decoder.decodeLong()
        val p = decoder.primitiveOrNull() ?: return 0L
        if (p is JsonNull) return 0L
        val s = p.content.trim()
        return s.toLongOrNull() ?: s.toDoubleOrNull()?.toLong() ?: 0L
    }
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

object LenientBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)
    override fun deserialize(decoder: Decoder): Boolean {
        if (decoder !is JsonDecoder) return decoder.decodeBoolean()
        val p = decoder.primitiveOrNull() ?: return false
        if (p is JsonNull) return false
        return when (p.content.trim().lowercase()) {
            "true", "1", "yes" -> true
            else -> false
        }
    }
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

/** Accepts a string, a number, or null and always yields a (possibly empty) string. */
object LenientStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String {
        if (decoder !is JsonDecoder) return decoder.decodeString()
        val p = decoder.primitiveOrNull() ?: return ""
        if (p is JsonNull) return ""
        return p.content
    }
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}
