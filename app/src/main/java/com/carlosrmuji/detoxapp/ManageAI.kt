package com.carlosrmuji.detoxapp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject

suspend fun callOpenAI(prompt: String): String {
    val client = OkHttpClient()
    val cloudFunctionUrl = "https://us-central1-detoxapp-b7f2d.cloudfunctions.net/callOpenAI"

    // --- CAMBIO #1:
    // En lugar de enviar sólo { "prompt": prompt }, le pasamos al servidor TODO el array `messages`
    // con el mensaje de sistema + el mensaje de usuario.
    val json = JSONObject().apply {
        put("messages", JSONArray().apply {
            // Mensaje de sistema: define el rol y comportamiento de la IA
            put(JSONObject().apply {
                put("role", "system")
                put("content",
                    """
    Eres un experto que ayuda a las personas a reducir el uso del móvil y mejorar su relación con la tecnología. Solo debes responder preguntas relacionadas con el uso de pantallas, distracción digital, uso excesivo de aplicaciones, hábitos digitales, bienestar digital y temas similares.

    Si el usuario hace una pregunta que NO tiene relación con estos temas, responde amablemente con una frase muy breve (máximo 20 palabras), diciendo que solo puedes ayudar con temas relacionados con el uso del móvil.

    Puedes hablar sobre cualquier aplicación que el usuario mencione (por ejemplo, TikTok, Instagram, YouTube, etc.). Pero bajo ningún concepto debes recomendar el uso de aplicaciones externas o de terceros para reducir el uso del móvil, ya que eso redirigiría al usuario fuera de esta app.

    Esta app cuenta con una funcionalidad que permite restringir el uso de aplicaciones específicas durante los horarios definidos por el propio usuario. Puedes recomendar esta funcionalidad **solo si es claramente útil en el contexto de la consulta** (por ejemplo, si el usuario quiere reducir el uso de una app concreta como TikTok o necesita limitar su uso en ciertos momentos del día).

    Si mencionas esta funcionalidad, especifica que se trata de una función propia de esta app y que se puede encontrar haciendo clic en el icono de menú arriba a la derecha, en la opción “Restringir Apps”.
    """.trimIndent()
                )
            })
            // Mensaje del usuario: su prompt concreto
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
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