package com.carlosrmuji.detoxapp.AI

import android.util.Log
import com.carlosrmuji.detoxapp.AIChatMessageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

suspend fun callOpenAI(prompt: String, history: List<AIChatMessageData>): String {
    val client = OkHttpClient()
    val cloudFunctionUrl = "https://us-central1-detoxapp-b7f2d.cloudfunctions.net/callOpenAI"

    val recentHistory = history.takeLast(2)

    // --- CAMBIO #1:
    // En lugar de enviar sólo { "prompt": prompt }, le pasamos al servidor TODO el array `messages`
    // con el mensaje de sistema + el mensaje de usuario.
    val json = JSONObject().apply {
        put("messages", JSONArray().apply {
            // --- Mensaje de sistema ---
            put(JSONObject().apply {
                put("role", "system")
                put("content", """
Eres un experto que ayuda a las personas a reducir el uso del móvil y mejorar su relación con la tecnología. Solo debes responder preguntas relacionadas con el uso de pantallas, distracción digital, uso excesivo de aplicaciones, hábitos digitales, bienestar digital y temas similares.

Si el usuario hace una pregunta que NO tiene relación con estos temas, responde amablemente con una frase muy breve (máximo 20 palabras), diciendo que solo puedes ayudar con temas relacionados con el uso del móvil.

Puedes hablar sobre cualquier aplicación que el usuario mencione (por ejemplo, TikTok, Instagram, YouTube, etc.). Pero bajo ningún concepto debes recomendar el uso de aplicaciones externas o de terceros para reducir el uso del móvil, ya que eso redirigiría al usuario fuera de esta app.

Esta app cuenta con una funcionalidad que permite restringir el uso de aplicaciones específicas durante los horarios definidos por el propio usuario (por ejemplo viernes, sabado y domingo no se puede acceder a la app especificada de 18 a 20) o estalbeciendo un limite de uso máximo diario, por ejemplo establecer para los miercoles y viernes no poder usar tiktok mas de 2 horas al dia, si se llega a esas 2 horas el usuario no podra acceder mas a dicha app durante el dia actual. Puedes recomendar esta funcionalidad **solo si es claramente útil en el contexto de la consulta** (por ejemplo, si el usuario quiere reducir el uso de una app concreta como TikTok o necesita limitar su uso en ciertos momentos del día).

Si mencionas esta funcionalidad, especifica que se trata de una función propia de esta app y que se puede encontrar en la parte inferior de la pantalla en la seccion “Restricciones”.
                """.trimIndent())
            })

            // --- Contexto explícito con los últimos mensajes ---
            if (recentHistory.isNotEmpty()) {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", buildString {
                        append("Aquí tienes la consulta anterior del usuario y la respuesta que la IA le dio. Úsalas solo como contexto si es relevante:\n\n")
                        recentHistory.forEach { msg ->
                            append(if (msg.sender == "user") "Usuario: " else "IA: ")
                            append(msg.text.trim())
                            append("\n\n")
                        }
                    })
                })
            }

            // --- Consulta actual ---
            put(JSONObject().apply {
                put("role", "user")
                put("content", "Esta es la consulta actual del usuario a la que debes responder teniendo en cuenta todas las instrucciones anteriores: \n\n$prompt")
            })
        })
    }

    // --- FIN DEL CAMBIO #1

    val body = RequestBody.create(
        "application/json".toMediaTypeOrNull(),
        json.toString()
    )

    val request = Request.Builder()
        .url(cloudFunctionUrl)
        .post(body)
        .build()

    return withContext(Dispatchers.IO) {
        try {
            Log.d("callOpenAI", "⏳ Enviando request a $cloudFunctionUrl con payload: $json")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            Log.d("callOpenAI", "📬 Código HTTP: ${response.code}")
            Log.d("callOpenAI", "📥 Respuesta cruda: $responseBody")

            if (response.isSuccessful && responseBody != null) {
                // --- CAMBIO #2:
                // Ahora el servidor devolverá JSON con { "reply": "...texto..." }
                // así que lo parseamos igual que antes:
                val root = JSONObject(responseBody)
                val reply = root.optString("reply", "")
                if (reply.isNotBlank()) reply
                else "⚠️ La IA devolvió una respuesta vacía."
            } else {
                "⚠️ Error al conectar con la IA. Código: ${response.code}, cuerpo: $responseBody"
            }
        } catch (e: Exception) {
            Log.e("callOpenAI", "❌ Excepción al llamar a la función", e)
            "⚠️ Error inesperado: ${e.localizedMessage}"
        }
    }
}