package com.translator.app

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Bloque de texto detectado con su posición en pantalla y traducción.
 */
data class TextBlock(
    val originalText: String,
    val translatedText: String,
    val bounds: Rect  // Posición exacta en la pantalla
)

/**
 * Hace OCR sobre un Bitmap y traduce cada bloque de inglés a español.
 * Todo funciona offline con ML Kit.
 */
class OcrTranslator {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val translatorOptions = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.SPANISH)
        .build()

    private val translator = Translation.getClient(translatorOptions)

    // Si el modelo de traducción ya fue descargado
    private var modelDescargado = false

    init {
        // Descargar modelo offline al iniciar (solo la primera vez, ~15MB)
        val conditions = com.google.mlkit.common.model.DownloadConditions.Builder()
            .requireWifi()
            .build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener { modelDescargado = true }
            .addOnFailureListener {
                // Si no hay WiFi, intentar sin condiciones
                translator.downloadModelIfNeeded()
                    .addOnSuccessListener { modelDescargado = true }
            }
    }

    /**
     * Procesa un bitmap: detecta texto en inglés y traduce cada bloque.
     * Llama a onResult con la lista de bloques traducidos.
     */
    fun procesarBitmap(bitmap: Bitmap, onResult: (List<TextBlock>) -> Unit) {
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val bloques = visionText.textBlocks

                if (bloques.isEmpty()) {
                    onResult(emptyList())
                    return@addOnSuccessListener
                }

                // Filtrar solo bloques que parecen inglés (tienen letras latinas)
                val bloquesIngles = bloques.filter { bloque ->
                    tieneTextoIngles(bloque.text)
                }

                if (bloquesIngles.isEmpty()) {
                    onResult(emptyList())
                    return@addOnSuccessListener
                }

                // Traducir todos los bloques
                traducirBloques(bloquesIngles, onResult)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    private fun traducirBloques(bloques: List<Text.TextBlock>, onResult: (List<TextBlock>) -> Unit) {
        val resultados = mutableListOf<TextBlock>()
        var pendientes = bloques.size

        for (bloque in bloques) {
            val textoOriginal = bloque.text
            val bounds = bloque.boundingBox ?: continue

            translator.translate(textoOriginal)
                .addOnSuccessListener { traduccion ->
                    // Solo agregar si la traducción es diferente al original
                    // (si son iguales probablemente ya estaba en español)
                    if (traduccion != textoOriginal) {
                        resultados.add(
                            TextBlock(
                                originalText = textoOriginal,
                                translatedText = traduccion,
                                bounds = bounds
                            )
                        )
                    }
                    pendientes--
                    if (pendientes == 0) onResult(resultados)
                }
                .addOnFailureListener {
                    pendientes--
                    if (pendientes == 0) onResult(resultados)
                }
        }
    }

    /**
     * Heurística simple: el texto tiene caracteres ASCII latinos
     * y no parece estar ya en español.
     */
    private fun tieneTextoIngles(texto: String): Boolean {
        if (texto.trim().length < 3) return false
        // Verificar que tenga al menos algunas letras
        val letras = texto.count { it.isLetter() }
        return letras >= 3
    }

    fun liberar() {
        recognizer.close()
        translator.close()
    }
}
