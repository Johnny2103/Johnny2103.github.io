package io.github.johnny2103.novagalerie.data.index

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import io.github.johnny2103.novagalerie.data.db.Embeddings
import kotlinx.coroutines.suspendCancellableCoroutine
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** A face found in an image: normalised box, identity embedding, quality 0..1 and a small crop. */
class DetectedFace(
    val box: RectF,
    val embedding: FloatArray,
    val quality: Float,
    val crop: Bitmap,
)

/**
 * Face pipeline: ML Kit finds faces (bundled model, offline), MobileFaceNet (TFLite, 112×112 → 192-d)
 * turns each aligned face crop into an embedding that can be compared with cosine similarity.
 */
class FaceEngine(private val context: Context) : Closeable {

    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.05f)
            .build(),
    )

    private val interpreter: Interpreter? by lazy { loadInterpreter() }
    private var inputSize = 112
    private var outputSize = 192

    val available: Boolean get() = interpreter != null

    private fun loadInterpreter(): Interpreter? = runCatching {
        val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.rewind()
        val options = Interpreter.Options().setNumThreads(max(2, Runtime.getRuntime().availableProcessors() / 2))
        Interpreter(buffer, options).also { interp ->
            val inShape = interp.getInputTensor(0).shape()
            if (inShape.size == 4) inputSize = inShape[1]
            val outShape = interp.getOutputTensor(0).shape()
            outputSize = outShape.last()
        }
    }.getOrNull()

    /** Detects faces in an upright bitmap and computes their embeddings. */
    suspend fun analyze(bitmap: Bitmap): List<DetectedFace> {
        val interp = interpreter ?: return emptyList()
        val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
        if (faces.isEmpty()) return emptyList()
        val result = ArrayList<DetectedFace>(faces.size)
        for (face in faces) {
            val box = face.boundingBox
            val minSide = min(box.width(), box.height())
            if (minSide < MIN_FACE_PX) continue
            val crop = alignedCrop(bitmap, face) ?: continue
            val embedding = embed(interp, crop)
            val sizeScore = min(1f, minSide / 110f)
            val yaw = abs(face.headEulerAngleY)
            val pitch = abs(face.headEulerAngleX)
            val poseScore = max(0f, cos(Math.toRadians(yaw.toDouble())).toFloat()) * max(0f, cos(Math.toRadians(pitch.toDouble())).toFloat())
            val quality = (sizeScore * poseScore).coerceIn(0f, 1f)
            val normalized = RectF(
                (box.left.toFloat() / bitmap.width).coerceIn(0f, 1f),
                (box.top.toFloat() / bitmap.height).coerceIn(0f, 1f),
                (box.right.toFloat() / bitmap.width).coerceIn(0f, 1f),
                (box.bottom.toFloat() / bitmap.height).coerceIn(0f, 1f),
            )
            val avatar = Bitmap.createScaledBitmap(crop, AVATAR_PX, AVATAR_PX, true)
            if (avatar !== crop) crop.recycle()
            result += DetectedFace(normalized, embedding, quality, avatar)
        }
        return result
    }

    /** Square crop with margin around the face, rotated so the eyes are level. */
    private fun alignedCrop(bitmap: Bitmap, face: Face): Bitmap? {
        val box = face.boundingBox
        val size = (max(box.width(), box.height()) * (1f + 2 * MARGIN)).toInt().coerceAtLeast(8)
        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        val angle = if (leftEye != null && rightEye != null) {
            Math.toDegrees(atan2((rightEye.y - leftEye.y).toDouble(), (rightEye.x - leftEye.x).toDouble())).toFloat()
        } else {
            face.headEulerAngleZ
        }
        return runCatching {
            // Draw the rotated source into a square target centred on the face.
            val target = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(target)
            val matrix = Matrix().apply {
                postTranslate(-cx, -cy)
                if (abs(angle) > 2f) postRotate(-angle)
                postTranslate(size / 2f, size / 2f)
            }
            canvas.drawBitmap(bitmap, matrix, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            target
        }.getOrNull()
    }

    private fun embed(interp: Interpreter, crop: Bitmap): FloatArray {
        val scaled = if (crop.width == inputSize && crop.height == inputSize) crop else Bitmap.createScaledBitmap(crop, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        scaled.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        if (scaled !== crop) scaled.recycle()
        val input = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            input.putFloat(((p shr 16 and 0xFF) - 127.5f) / 128f)
            input.putFloat(((p shr 8 and 0xFF) - 127.5f) / 128f)
            input.putFloat(((p and 0xFF) - 127.5f) / 128f)
        }
        input.rewind()
        val output = Array(1) { FloatArray(outputSize) }
        synchronized(this) { interp.run(input, output) }
        return Embeddings.normalize(output[0])
    }

    override fun close() {
        detector.close()
        runCatching { interpreter?.close() }
    }

    private companion object {
        const val MODEL_ASSET = "models/mobilefacenet.tflite"
        const val MIN_FACE_PX = 28
        const val MARGIN = 0.2f
        const val AVATAR_PX = 160
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { if (cont.isActive) cont.resume(it) }
    addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
