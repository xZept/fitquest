package com.example.fitquest

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import java.util.*

class RegisterActivity : AppCompatActivity() {

    private var selectedSex: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        setupInputFocusEffects()

        val mainLayout = findViewById<View>(R.id.registerLayout)
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, insets ->
            val systemInsets = insets.getInsets(Type.systemBars())
            view.setPadding(0, 0, 0, systemInsets.bottom)
            insets
        }

        val loginLink = findViewById<TextView>(R.id.tv_login_link)
        loginLink.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }

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

        val firstNameEditText = findViewById<EditText>(R.id.et_first_name)
        val lastNameEditText = findViewById<EditText>(R.id.et_last_name)
        val birthdayEditText = findViewById<EditText>(R.id.et_birthday)
        val ageTextView = findViewById<TextView>(R.id.tv_age)
        val usernameEditText = findViewById<EditText>(R.id.et_username)
        val emailEditText = findViewById<EditText>(R.id.et_email)
        val passwordEditText = findViewById<EditText>(R.id.et_password)
        val confirmPasswordEditText = findViewById<EditText>(R.id.et_confirm_password)
        val registerButton = findViewById<Button>(R.id.btn_register)

        // Filter: Allow only letters, space, and hyphen
        val nameFilter = InputFilter { source, _, _, _, _, _ ->
            val allowedPattern = Regex("^[a-zA-Z\\-\\s]+$")
            if (source.isEmpty() || source.matches(allowedPattern)) source else ""
        }

        fun setupCapitalization(editText: EditText) {
            editText.filters = arrayOf(nameFilter)
            editText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val original = s.toString()
                    if (original.isNotEmpty()) {
                        val capitalized = original.split(" ").joinToString(" ") {
                            it.lowercase().replaceFirstChar { char -> char.uppercase() }
                        }
                        if (capitalized != original) {
                            editText.removeTextChangedListener(this)
                            editText.setText(capitalized)
                            editText.setSelection(capitalized.length)
                            editText.addTextChangedListener(this)
                        }
                    }
                }
            })
        }

        setupCapitalization(firstNameEditText)
        setupCapitalization(lastNameEditText)

        // Date Picker & Age Calculation
        birthdayEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            DatePickerDialog(this, { _, y, m, d ->
                birthdayEditText.setText(String.format("%04d-%02d-%02d", y, m + 1, d))

                val today = Calendar.getInstance()
                val birthDate = Calendar.getInstance().apply {
                    set(y, m, d)
                }

                var age = today.get(Calendar.YEAR) - birthDate.get(Calendar.YEAR)
                if (today.get(Calendar.DAY_OF_YEAR) < birthDate.get(Calendar.DAY_OF_YEAR)) {
                    age--
                }

                ageTextView.text = "Age: $age"

            }, year, month, day).show()
        }

        // Password validation
        fun isValidPassword(password: String): Boolean {
            val pattern = Regex("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#\$%^&+=!]).{8,}$")
            return pattern.matches(password)
        }

        // Email validation
        fun isValidEmail(email: String): Boolean {
            return Patterns.EMAIL_ADDRESS.matcher(email).matches()
        }

        registerButton.setOnClickListener {
            val firstName = firstNameEditText.text.toString().trim()
            val lastName = lastNameEditText.text.toString().trim()
            val birthday = birthdayEditText.text.toString().trim()
            val username = usernameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            when {
                firstName.isEmpty() || lastName.isEmpty() || birthday.isEmpty() || username.isEmpty() ||
                        email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || selectedSex == null -> {
                    Toast.makeText(this, "Please fill out all fields.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                !isValidEmail(email) -> {
                    Toast.makeText(this, "Invalid email format.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                password != confirmPassword -> {
                    Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                !isValidPassword(password) -> {
                    Toast.makeText(
                        this,
                        "Password must be 8+ characters with uppercase, lowercase, digit, and special character.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                else -> {
                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()

                    listOf(
                        firstNameEditText, lastNameEditText, birthdayEditText,
                        usernameEditText, emailEditText, passwordEditText, confirmPasswordEditText
                    ).forEach { it.text.clear() }

                    selectedSex = null
                    ivMale.setBackgroundResource(R.drawable.sex_container)
                    ivFemale.setBackgroundResource(R.drawable.sex_container)
                    ageTextView.text = "Age:"

                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            }
        }
    }

    private fun setupInputFocusEffects() {
        val inputs = listOf(
            R.id.et_first_name,
            R.id.et_last_name,
            R.id.et_birthday,
            R.id.et_username,
            R.id.et_email,
            R.id.et_password,
            R.id.et_confirm_password
        )

        for (id in inputs) {
            findViewById<EditText>(id).setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundResource(
                    if (hasFocus) R.drawable.user_input_bg_selected
                    else R.drawable.user_input_bg
                )
            }
        }
    }
}
