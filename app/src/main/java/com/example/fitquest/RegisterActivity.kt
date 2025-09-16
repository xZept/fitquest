package com.example.fitquest

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.User
import com.example.fitquest.repository.FitquestRepository
import kotlinx.coroutines.launch
import java.util.*

class RegisterActivity : AppCompatActivity() {
    private lateinit var repository: FitquestRepository

    private var selectedSex: String? = null
    private lateinit var male: ImageView
    private lateinit var female: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize repository
        repository = FitquestRepository(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize sex options
        male = findViewById(R.id.iv_male)
        female = findViewById(R.id.iv_female)

        male.setOnClickListener {
            selectedSex = "Male"
            highlightSelected(male, female)
        }

        female.setOnClickListener {
            selectedSex = "Female"
            highlightSelected(female, male)
        }

        // hides the system navigation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }

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

            val datePickerDialog = DatePickerDialog(this, { _, y, m, d ->
                birthdayEditText.setText(String.format("%04d-%02d-%02d", y, m + 1, d))

                val today = Calendar.getInstance()
                val birthDate = Calendar.getInstance().apply { set(y, m, d) }

                var age = today.get(Calendar.YEAR) - birthDate.get(Calendar.YEAR)
                if (today.get(Calendar.DAY_OF_YEAR) < birthDate.get(Calendar.DAY_OF_YEAR)) {
                    age--
                }

                ageTextView.text = "Age: $age"

            }, year, month, day)

            // Use spinner instead of calendar view
            datePickerDialog.datePicker.calendarViewShown = false
            datePickerDialog.datePicker.spinnersShown = true

            // Prevent selecting future dates
            datePickerDialog.datePicker.maxDate = System.currentTimeMillis()

            datePickerDialog.show()
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
            val age = ageTextView.text.toString().replace("Age:", "").trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
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

                    val newUser = User(
                        firstName = firstName.trim(),
                        lastName = lastName.trim(),
                        birthday = birthday.trim(), // placeholder until you add DatePicker
                        age = age.toInt(),
                        sex = selectedSex!!.trim(),
                        username = username.trim(),
                        email = email.trim(),
                        password = password.trim()
                    )

                    Log.d("FitquestDB", "Registering new user: $newUser")

                    lifecycleScope.launch {
                        repository.insertAll(newUser)
                        Log.d("FitquestDB", "User inserted into DB successfully.")

                        // Debug: print all users after insert
                        val allUsers = repository.getAllUsers()
                        Log.d("FitquestDB", "All users in DB after insert: $allUsers")

                        Toast.makeText(
                            this@RegisterActivity,
                            "Registration Successful!",
                            Toast.LENGTH_SHORT
                        ).show()

                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()
                    }

                    Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()

                    listOf(
                        firstNameEditText, lastNameEditText, birthdayEditText,
                        usernameEditText, emailEditText, passwordEditText, confirmPasswordEditText
                    ).forEach { it.text.clear() }

                    selectedSex = null
                    male.setBackgroundResource(R.drawable.sex_container)
                    female.setBackgroundResource(R.drawable.sex_container)
                    ageTextView.text = "Age:"
                }
            }
        }
    }

    private fun highlightSelected(selected: ImageView, unselected: ImageView) {
        selected.setBackgroundResource(R.drawable.sex_container_selected)
        unselected.setBackgroundResource(R.drawable.sex_container)
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
