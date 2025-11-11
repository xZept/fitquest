package com.example.fitquest

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.repository.FitquestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import android.widget.ImageButton

class WeightPromptActivity : AppCompatActivity() {

    companion object {
        private const val MIN_WEIGHT = 30.0
        private const val MAX_WEIGHT = 300.0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_weight_prompt)

        val et = findViewById<EditText>(R.id.et_weight_today)
        val btnSave = findViewById<ImageButton>(R.id.btn_save_weight)


        btnSave.setOnClickListener {
            val w = et.text.toString().trim().toDoubleOrNull()

            if (w == null || w < MIN_WEIGHT || w > MAX_WEIGHT) {
                Toast.makeText(this, "Enter a realistic weight (30–300 kg).", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val uid = DataStoreManager.getUserId(this@WeightPromptActivity).first()
                if (uid == -1) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@WeightPromptActivity, "Please log in first.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                val db = AppDatabase.getInstance(applicationContext)

                val profile = db.userProfileDAO().getProfileByUserId(uid)
                if (profile != null) {
                    db.userProfileDAO().update(profile.copy(weight = w.roundToInt()))
                    FitquestRepository(this@WeightPromptActivity).computeAndSaveMacroPlan(uid)
                }

                db.weightLogDao().insert(
                    com.example.fitquest.database.WeightLog(
                        userId = uid,
                        loggedAt = System.currentTimeMillis(),
                        weightKg = w.toFloat()
                    )
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@WeightPromptActivity, "Weight saved!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
