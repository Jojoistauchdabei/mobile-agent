package com.mobileagent.router

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
import kotlin.math.ln
import kotlin.math.max

enum class LayaQuestionType(val code: Int) {
    CHOICE(0),
    SCORE(1),
    NOUL(2),
}

data class LayaQuestion(
    val id: String,
    val type: LayaQuestionType,
    val instructions: String,
    val options: List<String>,
    val renderedOptions: List<String> = options,
)

data class LayaOnnxConfig(
    val questions: List<LayaQuestion> = AndroidLayaQuestions.all,
    val maxLength: Int = 1_024,
    val headMaxLength: Int = 256,
    val inputIdsName: String = "input_ids",
    val attentionMaskName: String = "attention_mask",
    val markerPositionName: String = "marker_pos",
    val markerMaskName: String = "marker_mask",
    val questionTypeName: String = "qtype",
    val outputName: String = "logits",
)

object AndroidLayaQuestions {
    val intent = LayaQuestion(
        id = "intent",
        type = LayaQuestionType.CHOICE,
        instructions = "Welche Absicht hat der Nutzer?",
        options = listOf("chat", "search", "device_action", "unsafe"),
        renderedOptions = listOf(
            "chat: allgemeine Unterhaltung oder Frage",
            "search: Informationen aus dem Internet recherchieren",
            "device_action: eine Aktion auf dem Android-Gerät ausführen",
            "unsafe: unsichere oder schädliche Anweisung",
        ),
    )
    val needsSearch = LayaQuestion(
        id = "needs_search",
        type = LayaQuestionType.NOUL,
        instructions = "Muss dafür eine Internetrecherche durchgeführt werden?",
        options = listOf("false", "true"),
        renderedOptions = listOf("false: nein", "true: ja"),
    )
    val needsAction = LayaQuestion(
        id = "needs_action",
        type = LayaQuestionType.NOUL,
        instructions = "Muss eine Aktion auf dem Android-Gerät ausgeführt werden?",
        options = listOf("false", "true"),
        renderedOptions = listOf("false: nein", "true: ja"),
    )
    val blocked = LayaQuestion(
        id = "blocked",
        type = LayaQuestionType.NOUL,
        instructions = "Soll die Anweisung aus Sicherheitsgründen blockiert werden?",
        options = listOf("false", "true"),
        renderedOptions = listOf("false: nein", "true: ja"),
    )
    val all = listOf(intent, needsSearch, needsAction, blocked)
}

class OnnxLayaBackend(
    private val modelFile: File,
    private val tokenizerFile: File,
    private val config: LayaOnnxConfig = LayaOnnxConfig(),
) : LayaBackend, Closeable {
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    private val tokenizer by lazy { JsonLayaTokenizer.load(tokenizerFile) }
    private val sessionDelegate = lazy<OrtSession> {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
        }
        environment.createSession(modelFile.absolutePath, options)
    }
    private val session by sessionDelegate

    override fun predict(text: String): LayaPrediction? {
        if (!modelFile.isFile || !tokenizerFile.isFile) return null
        val encoded = config.questions.map { question -> encode(question, text) }
        val maxLength = encoded.maxOf { it.inputIds.size }
        val markerWidth = encoded.maxOf { it.markerPositions.size }
        val batchSize = encoded.size
        val inputIds = LongArray(batchSize * maxLength)
        val attentionMask = LongArray(batchSize * maxLength)
        val markerPositions = LongArray(batchSize * markerWidth)
        val markerMask = BooleanArray(batchSize * markerWidth)
        val questionTypes = LongArray(batchSize)

        encoded.forEachIndexed { row, item ->
            System.arraycopy(item.inputIds, 0, inputIds, row * maxLength, item.inputIds.size)
            System.arraycopy(item.attentionMask, 0, attentionMask, row * maxLength, item.attentionMask.size)
            System.arraycopy(item.markerPositions, 0, markerPositions, row * markerWidth, item.markerPositions.size)
            System.arraycopy(item.markerMask, 0, markerMask, row * markerWidth, item.markerMask.size)
            questionTypes[row] = config.questions[row].type.code.toLong()
        }

        val inputIdsBuffer = LongBuffer.wrap(inputIds)
        val attentionBuffer = LongBuffer.wrap(attentionMask)
        val markerPositionBuffer = LongBuffer.wrap(markerPositions)
        val markerMaskBuffer = ByteBuffer.allocate(markerMask.size).order(ByteOrder.nativeOrder()).apply {
            markerMask.forEach { value -> put(if (value) 1 else 0) }
            flip()
        }
        val questionTypeBuffer = LongBuffer.wrap(questionTypes)
        val tensors = listOf(
            OnnxTensor.createTensor(environment, inputIdsBuffer, longArrayOf(batchSize.toLong(), maxLength.toLong())),
            OnnxTensor.createTensor(environment, attentionBuffer, longArrayOf(batchSize.toLong(), maxLength.toLong())),
            OnnxTensor.createTensor(environment, markerPositionBuffer, longArrayOf(batchSize.toLong(), markerWidth.toLong())),
            OnnxTensor.createTensor(environment, markerMaskBuffer, longArrayOf(batchSize.toLong(), markerWidth.toLong()), OnnxJavaType.BOOL),
            OnnxTensor.createTensor(environment, questionTypeBuffer, longArrayOf(batchSize.toLong())),
        )
        val names = listOf(
            config.inputIdsName,
            config.attentionMaskName,
            config.markerPositionName,
            config.markerMaskName,
            config.questionTypeName,
        )

        return try {
            val inputs = names.zip(tensors).associate { it.first to it.second }
            val result = session.run(inputs)
            try {
                val output = result[config.outputName] as? OnnxTensor
                if (output == null) {
                    null
                } else {
                    try {
                        val shape = output.info.shape
                        val rowWidth = shape.getOrNull(1)?.toInt() ?: markerWidth
                        val buffer = output.floatBuffer
                        val probabilities = config.questions.indices.associate { row ->
                            val question = config.questions[row]
                            val count = question.options.size
                            val values = FloatArray(count) { index ->
                                buffer.get(row * rowWidth + index)
                            }
                            question.id to softmax(values)
                        }
                        decode(probabilities)
                    } finally {
                        output.close()
                    }
                }
            } finally {
                result.close()
            }
        } finally {
            tensors.forEach { it.close() }
        }
    }

    override fun close() {
        if (sessionDelegate.isInitialized()) session.close()
    }

    private fun decode(probabilities: Map<String, FloatArray>): LayaPrediction? {
        val intent = probabilities[AndroidLayaQuestions.intent.id] ?: return null
        val search = probabilities[AndroidLayaQuestions.needsSearch.id] ?: return null
        val action = probabilities[AndroidLayaQuestions.needsAction.id] ?: return null
        val blocked = probabilities[AndroidLayaQuestions.blocked.id] ?: return null
        val intentIndex = intent.indices.maxByOrNull { intent[it] } ?: return null
        val intentName = AndroidLayaQuestions.intent.options[intentIndex]
        return LayaPrediction(
            intent = intentName,
            needsSearch = search.getOrElse(1) { 0f } >= 0.5f,
            needsAction = action.getOrElse(1) { 0f } >= 0.5f,
            blocked = blocked.getOrElse(1) { 0f } >= 0.5f || intentName == "unsafe",
            confidence = minOf(confidence(intent), confidence(search), confidence(action), confidence(blocked)).toDouble(),
        )
    }

    private fun encode(question: LayaQuestion, state: String): EncodedQuestion {
        val ids = ArrayList<Int>()
        val markerPositions = ArrayList<Int>()
        val markerMask = ArrayList<Boolean>()
        ids += tokenizer.clsTokenId
        ids += tokenizer.encode("${question.type.name.lowercase()} question: ${question.instructions}").toList()
        ids += tokenizer.sepTokenId
        val budget = max(4, (config.headMaxLength - 16) / question.renderedOptions.size)
        question.renderedOptions.forEach { option ->
            markerPositions += ids.size
            markerMask += true
            ids += tokenizer.maskTokenId
            ids += tokenizer.encode(" $option").take(budget).toList()
        }
        ids += tokenizer.sepTokenId
        val room = max(0, config.maxLength - ids.size - 1)
        ids += tokenizer.encode(state).take(room).toList()
        ids += tokenizer.sepTokenId
        val inputIds = ids.take(config.maxLength).map { it.toLong() }.toLongArray()
        val clippedMarkers = markerPositions.map { it.toLong().coerceAtMost(inputIds.size - 1L) }.toLongArray()
        val clippedMask = markerMask.mapIndexed { index, value -> value && index < clippedMarkers.size }.toBooleanArray()
        val attention = LongArray(inputIds.size) { 1L }
        return EncodedQuestion(inputIds, attention, clippedMarkers, clippedMask)
    }

    private fun softmax(values: FloatArray): FloatArray {
        if (values.isEmpty()) return values
        val max = values.max()
        val exponentials = FloatArray(values.size) { kotlin.math.exp(values[it] - max) }
        val sum = exponentials.sum()
        return FloatArray(values.size) { exponentials[it] / sum }
    }

    private fun confidence(values: FloatArray): Float {
        if (values.size <= 1) return 1f
        var entropy = 0f
        values.forEach { probability ->
            if (probability > 0f) entropy -= probability * ln(probability.toDouble()).toFloat()
        }
        return (1f - entropy / ln(values.size.toDouble()).toFloat()).coerceIn(0f, 1f)
    }

    private data class EncodedQuestion(
        val inputIds: LongArray,
        val attentionMask: LongArray,
        val markerPositions: LongArray,
        val markerMask: BooleanArray,
    )
}

object LayaBackendFactory {
    fun createIfAvailable(modelsDirectory: File): LayaBackend? {
        val model = File(modelsDirectory, "laya-multilingual.onnx")
        val tokenizer = File(modelsDirectory, "laya-tokenizer.json")
        if (!model.isFile || !tokenizer.isFile) return null
        return runCatching { OnnxLayaBackend(model, tokenizer) }.getOrNull()
    }
}
