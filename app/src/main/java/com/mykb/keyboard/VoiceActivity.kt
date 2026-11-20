package com.mykb.keyboard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast

/** Transparent helper: shows the system voice-typing dialog and hands the text back to the keyboard. */
class VoiceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lang = intent.getStringExtra("lang") ?: "bn-BD"
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, if (lang.startsWith("bn")) "বলুন…" else "Speak…")
        try {
            startActivityForResult(i, 77)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "ভয়েস টাইপিং সার্ভিস (Google) পাওয়া যায়নি", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 77 && resultCode == RESULT_OK) {
            val r = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (r != null && r.isNotEmpty()) VoiceBridge.pending = r[0]
        }
        finish()
    }
}
