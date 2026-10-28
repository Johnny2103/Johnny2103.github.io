package io.github.johnny2103.novagalerie.data.db

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Helpers for storing float vectors as little-endian byte arrays. */
object Embeddings {

    fun toBytes(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.getFloat() }
    }

    fun normalize(vector: FloatArray): FloatArray {
        var sum = 0f
        for (v in vector) sum += v * v
        val norm = sqrt(sum)
        if (norm <= 1e-6f) return vector
        return FloatArray(vector.size) { vector[it] / norm }
    }

    /** Cosine similarity between a unit vector and an arbitrary (e.g. summed) vector. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return -1f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom <= 1e-6f) -1f else dot / denom
    }

    fun add(sum: FloatArray, v: FloatArray): FloatArray {
        if (sum.isEmpty()) return v.copyOf()
        val out = sum.copyOf()
        for (i in out.indices) out[i] += v[i]
        return out
    }
}
