package com.example.detoxapp


import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import androidx.preference.PreferenceManager    // ← IMPORT CORREGIDO



private const val TAG = "DetoxApp"

class TermsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "TermsActivity.onCreate START")
        try {
            setContentView(R.layout.activity_terms)
            Log.d(TAG, "TermsActivity: Layout inflated OK")

            val acceptButton = findViewById<Button>(R.id.button_accept)
            if (acceptButton == null) {
                Log.e(TAG, "TermsActivity: button_accept is NULL!")
            } else {
                Log.d(TAG, "TermsActivity: button_accept found")
            }

            acceptButton?.setOnClickListener {
                Log.d(TAG, "TermsActivity: acceptButton clicked")
                try {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                    prefs.edit().putBoolean("accepted_terms", true).apply()
                    Log.d(TAG, "TermsActivity: accepted_terms set to true")

                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "TermsActivity: error saving prefs or launching MainActivity", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "TermsActivity.onCreate: unexpected error", e)
            finish() // cerramos para no dejar la activity en mal estado
        }
        Log.d(TAG, "TermsActivity.onCreate END")
    }
}