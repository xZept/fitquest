package com.example.fitquest

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class RegisterActivity : AppCompatActivity() {

    private var selectedSex: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register) // Make sure you named your layout file correctly

        setupInputFocusEffects()

        val ivMale = findViewById<ImageView>(R.id.iv_male)
        val ivFemale = findViewById<ImageView>(R.id.iv_female)

        ivMale.setOnClickListener {
            ivMale.setBackgroundResource(R.drawable.sex_container_selected)
            ivFemale.setBackgroundResource(R.drawable.sex_container)
            selectedSex = "Male"
        }

        ivFemale.setOnClickListener {
            ivFemale.setBackgroundResource(R.drawable.sex_container_selected)
            ivMale.setBackgroundResource(R.drawable.sex_container)
            selectedSex = "Female"
        }
    }

    private fun setupInputFocusEffects() {
        val inputs = listOf(
            findViewById<EditText>(R.id.et_first_name),
            findViewById<EditText>(R.id.et_last_name),
            findViewById<EditText>(R.id.et_birthday),
            findViewById<EditText>(R.id.et_username),
            findViewById<EditText>(R.id.et_email),
            findViewById<EditText>(R.id.et_password),
            findViewById<EditText>(R.id.et_confirm_password)
        )

        for (input in inputs) {
            input.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.setBackgroundResource(R.drawable.user_input_bg_selected)
                } else {
                    view.setBackgroundResource(R.drawable.user_input_bg)
                }
            }
        }
    }

}
