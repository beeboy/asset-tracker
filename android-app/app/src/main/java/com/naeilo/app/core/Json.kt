package com.naeilo.app.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant

/** 아이폰 JSONEncoder/Decoder 와 서로 읽히게 맞춘 설정 하나만 쓴다 (모르는 키 무시, 없는 키는 기본값, null 은 안 씀) */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
val NJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }

/** Swift Date 기본 형식: 2001-01-01 기준 초 (Double) */
object SwiftDate : KSerializer<Instant> {
    const val REF = 978_307_200L
    override val descriptor = PrimitiveSerialDescriptor("SwiftDate", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Instant) =
        encoder.encodeDouble(value.epochSecond - REF + value.nano / 1e9)
    override fun deserialize(decoder: Decoder): Instant {
        val s = decoder.decodeDouble() + REF
        val sec = Math.floor(s).toLong()
        return Instant.ofEpochSecond(sec, ((s - sec) * 1e9).toLong())
    }
}

/** dateEncodingStrategy = .iso8601 인 곳 (초 단위, Z) */
object IsoDate : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("IsoDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(Instant.ofEpochSecond(value.epochSecond).toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
