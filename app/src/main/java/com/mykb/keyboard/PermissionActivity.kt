package com.mykb.keyboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast

/** Transparent helper that asks for the microphone permission (a keyboard cannot show that dialog itself). */
class PermissionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 5)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "মাইক্রোফোনের অনুমতি না দিলে ভয়েস টাইপিং চলবে না", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "অনুমতি পাওয়া গেছে। এবার 🎤 চাপুন", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
