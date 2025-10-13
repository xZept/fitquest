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
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.database.User
import com.example.fitquest.database.UserProfile
import com.example.fitquest.repository.FitquestRepository
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt
import android.graphics.BitmapFactory
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import android.graphics.drawable.AnimationDrawable
import androidx.core.content.ContextCompat
import android.view.MotionEvent
import android.widget.*
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.fitquest.database.AppDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch




class RegisterActivity : AppCompatActivity() {
    private lateinit var repository: FitquestRepository

    private var selectedSex: String? = null
    private lateinit var male: ImageView
    private lateinit var female: ImageView

    private lateinit var pressAnim: android.view.animation.Animation
    private var bgSprite: SpriteSheetDrawable? = null

    private lateinit var welcomeBanner: TextView

    private var bannerHideRunnable: Runnable? = null

    private lateinit var help: Map<Int, String>

    override fun onCreate(savedInstanceState: Bundle?) {
        repository = FitquestRepository(this)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        welcomeBanner = findViewById(R.id.tv_welcome_banner)

        help = mapOf(
            R.id.et_first_name to "First name — shown in your profile parchment.",
            R.id.et_last_name to "Last name — shown in your profile parchment.",
            R.id.et_birthday to "Birthday — we compute your age",
            R.id.et_username to "Username — your in-realm name.",
            R.id.et_email to "Email — used for login",
            R.id.et_password to "Password — use 8+ chars with upper/lower, a number, and a symbol.",
            R.id.et_confirm_password to "Confirm password — retype to avoid typos before we forge your account.",
            R.id.et_height to "Height (cm) — helps size movements and compute metrics.",
            R.id.et_weight to "Weight (kg) — tunes calorie estimates.",
            R.id.et_goal_weight to "Goal weight — we’ll save exactly what you enter here.",
            R.id.spinner_activity_levels to "Activity level — your usual daily movement; we use this to compute calories (TDEE).",
            R.id.iv_male to "Avatar — choose your gender.",
            R.id.iv_female to "Avatar — choose your gender.",
            R.id.tv_fitness_goal to "Fitness goal is auto-set from your weight and goal weight."

        )



        fun wireSpinnerHint(spinner: Spinner, idForCopy: Int) {
            spinner.setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) showBanner(help[idForCopy] ?: "")
                false
            }
        }

        // Show for a few seconds on entry
        lifecycleScope.launch {
            // prepare & fade in
            welcomeBanner.alpha = 0f
            welcomeBanner.visibility = View.VISIBLE
            welcomeBanner.animate().alpha(1f).setDuration(200).withLayer().start()

            // stay visible for N ms
            delay(8000)

            // fade out, then hide
            welcomeBanner.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction { welcomeBanner.visibility = View.GONE }
                .withLayer()
                .start()
        }

        // === Animated background (spritesheet) ===
        val root = findViewById<ConstraintLayout>(R.id.registerLayout)

// Prevent density scaling to keep frame grid exact
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }

        val sheet = BitmapFactory.decodeResource(
            resources,
            R.drawable.bg_page_register_spritesheet,
            opts
        )


        bgSprite = SpriteSheetDrawable(
            sheet = sheet,
            rows = 1,
            cols = 12,
            fps = 12,
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
        )

    // Put the animated sheet behind everything
        root.background = bgSprite


        // Initialize sex options
        male = findViewById(R.id.iv_male)
        female = findViewById(R.id.iv_female)

        male.setOnClickListener {
            showBanner(help[R.id.iv_male] ?: "")
            selectedSex = "Male"
            highlightSelected(male, female)
        }
        female.setOnClickListener {
            showBanner(help[R.id.iv_female] ?: "")
            selectedSex = "Female"
            highlightSelected(female, male)
        }


        // Hide system navigation
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

//        setupInputFocusEffects()

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

        // EditTexts
        wireEditText(findViewById(R.id.et_first_name))
        wireEditText(findViewById(R.id.et_last_name))
        wireEditText(findViewById(R.id.et_username))
        wireEditText(findViewById(R.id.et_email))
        wireEditText(findViewById(R.id.et_password))
        wireEditText(findViewById(R.id.et_confirm_password))
        wireEditText(findViewById(R.id.et_height))
        wireEditText(findViewById(R.id.et_weight))

        // Birthday (click-only field)
        val birthdayEditText = findViewById<EditText>(R.id.et_birthday).apply {
            background = ContextCompat.getDrawable(context, R.drawable.user_input_bg_selector)
            setOnClickListener {
                showBanner(help[R.id.et_birthday] ?: "")   // ← add this
                it.pulseThenSetActivated(text.isNotBlank())
                // open date picker after…
            }
        }
//        wireEditText(birthdayEditText) // optional: keeps state by value if you later allow typing

        // Form fields
        val firstNameEditText = findViewById<EditText>(R.id.et_first_name)
        val lastNameEditText = findViewById<EditText>(R.id.et_last_name)
        val ageTextView = findViewById<TextView>(R.id.tv_age)
        val usernameEditText = findViewById<EditText>(R.id.et_username)
        val emailEditText = findViewById<EditText>(R.id.et_email)
        val passwordEditText = findViewById<EditText>(R.id.et_password)
        val confirmPasswordEditText = findViewById<EditText>(R.id.et_confirm_password)
        val heightEditText = findViewById<EditText>(R.id.et_height)
        val weightEditText = findViewById<EditText>(R.id.et_weight)
        val goalWeightEditText = findViewById<EditText>(R.id.et_goal_weight)
        val registerButton = findViewById<ImageButton>(R.id.btn_register)
        val tvFitnessGoal = findViewById<TextView>(R.id.tv_fitness_goal)
        val spinnerActivityLevel = findViewById<Spinner>(R.id.spinner_activity_levels)
//        wireSpinner(spinnerActivityLevel, placeholderIndex = 0) // adjust index if no placeholder
//        wireSpinner(spinnerGoal, placeholderIndex = 0)
        wireSpinnerHint(spinnerActivityLevel, R.id.spinner_activity_levels)
        // Constrain inputs
        heightEditText.applyNumericConstraints(MIN_HEIGHT_CM, MAX_HEIGHT_CM, maxDecimals = 1)
        weightEditText.applyNumericConstraints(MIN_WEIGHT_KG, MAX_WEIGHT_KG, maxDecimals = 1)
        goalWeightEditText.applyNumericConstraints(MIN_WEIGHT_KG, MAX_WEIGHT_KG, maxDecimals = 1)

        // After you define goalWeightEditText:
        wireEditText(goalWeightEditText) // add this

        // Small helper to compare decimals
        fun nearlyEqual(a: Float, b: Float, eps: Float = 0.5f) = kotlin.math.abs(a - b) < eps

        // Compute goal from weight & goal weight
        fun deriveFitnessGoal(): String? {
            val w = weightEditText.text.toString().toFloatOrNull() ?: return null
            val gw = goalWeightEditText.text.toString().toFloatOrNull() ?: return null
            return when {
                nearlyEqual(w, gw) -> "Maintain Weight"
                gw < w             -> "Lose Weight"
                else               -> "Build Muscle"
            }
        }

        // Update the label whenever inputs change
        fun updateFitnessGoalUI() {
            val goal = deriveFitnessGoal()
            tvFitnessGoal.text = "Fitness Goal: " + (goal ?: "—")
        }

        // Disable register when invalid
        fun formIsValid(): Boolean {
            val h = heightEditText.text.toString().toFloatOrNull()
            val w = weightEditText.text.toString().toFloatOrNull()
            val gw = goalWeightEditText.text.toString().toFloatOrNull()
            return h != null && w != null && gw != null &&
                    isHeightValid(h) && isWeightValid(w) && isWeightValid(gw)
        }

        fun updateRegisterEnabled() {
            registerButton.isEnabled = formIsValid()
            registerButton.alpha = if (registerButton.isEnabled) 1f else 0.6f
        }

        heightEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateRegisterEnabled() }
            override fun afterTextChanged(s: Editable?) {}
        })
        weightEditText.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateRegisterEnabled()
                updateFitnessGoalUI()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })

        goalWeightEditText.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateRegisterEnabled()
                updateFitnessGoalUI()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
        })

        updateRegisterEnabled()


        // Set up spinners
        ArrayAdapter.createFromResource(
            this,
            R.array.activity_levels,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerActivityLevel.adapter = adapter
        }

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
            showBanner(help[R.id.et_birthday] ?: "")
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

            datePickerDialog.datePicker.calendarViewShown = false
            datePickerDialog.datePicker.spinnersShown = true
            datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
            datePickerDialog.show()
        }

        fun isValidPassword(password: String): Boolean {
            val pattern = Regex("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#\$%^&+=!_]).{8,}$")
            return pattern.matches(password)
        }

        fun isValidEmail(email: String): Boolean {
            return Patterns.EMAIL_ADDRESS.matcher(email).matches()
        }

        registerButton.setOnClickListener {
            it.startAnimation(pressAnim)
            val firstName = firstNameEditText.text.toString().trim()
            val lastName = lastNameEditText.text.toString().trim()
            val birthday = birthdayEditText.text.toString().trim()
            val username = usernameEditText.text.toString().trim()
            val ageString = ageTextView.text.toString().replace("Age:", "").trim()
            val age = if (ageString.isNotEmpty()) ageString.toInt() else 0
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString()
            val heightVal = heightEditText.text.toString().toFloatOrNull()
            val weightVal = weightEditText.text.toString().toFloatOrNull()
            val activityLevel = spinnerActivityLevel.selectedItem.toString()

            when {
                firstName.isEmpty() || lastName.isEmpty() || birthday.isEmpty() || username.isEmpty() ||
                        email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() || selectedSex == null || age == 0 -> {
                    Toast.makeText(this, "Please fill out all fields.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                heightVal == null || !isHeightValid(heightVal) -> {
                    Toast.makeText(this, "Enter realistic height (${MIN_HEIGHT_CM.toInt()}–${MAX_HEIGHT_CM.toInt()} cm).", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                weightVal == null || !isWeightValid(weightVal) -> {
                    Toast.makeText(this, "Enter realistic weight (${MIN_WEIGHT_KG.toInt()}–${MAX_WEIGHT_KG.toInt()} kg).", Toast.LENGTH_SHORT).show()
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
                    lifecycleScope.launch {

                        val newUser = User(
                            firstName = firstName,
                            lastName = lastName,
                            birthday = birthday,
                            age = age,
                            sex = selectedSex!!,
                            username = username,
                            email = email,
                            password = password, // Note: Passwords should be hashed in a real app
                        )
                        val newUserId = repository.insertUser(newUser)
                        Log.d("FitquestDB", "Inserted user id: $newUserId")
                        val goalWeightVal = goalWeightEditText.text.toString().toFloatOrNull()
                        val goal = deriveFitnessGoal()!!

                        val newUserProfile = UserProfile(
                            userId = newUserId.toInt(),
                            height = heightVal.roundToInt(),
                            weight = weightVal.roundToInt(),
                            goalWeight = goalWeightVal?.roundToInt(), // NEW
                            activityLevel = activityLevel,
                            goal = goal
                        )
                        repository.insertUserProfile(newUserProfile)

                        val db = AppDatabase.getInstance(applicationContext)

                        db.weightLogDao().insert(
                            com.example.fitquest.database.WeightLog(
                                userId   = newUserId.toInt(),
                                loggedAt = System.currentTimeMillis(),
                                weightKg = weightVal!!.toFloat()   // you already validated non-null and range
                            )
                        )

                        Log.d("FitquestDB", "User and UserProfile inserted.")
                        Log.d("FitquestDB", "Registering new user: $newUser")
                        Log.d("FitquestDB", "Adding new user profile: $newUserProfile")

                        // Calculate macros
                        repository.computeAndSaveMacroPlan(newUserId.toInt())



                        Toast.makeText(
                            this@RegisterActivity,
                            "Registration Successful!",
                            Toast.LENGTH_SHORT
                        ).show()

                        startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                        finish()

                        // --- END OF FINAL CHANGES ---
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bgSprite?.start()
    }

    override fun onPause() {
        bgSprite?.stop()
        super.onPause()
    }

    override fun onStop() {
        welcomeBanner.animate().cancel()
        if (welcomeBanner.isVisible) {
            welcomeBanner.visibility = View.GONE
            welcomeBanner.alpha = 0f
        }
        super.onStop()
    }

    override fun onDestroy() {
        bgSprite?.stop()
        bgSprite = null
        super.onDestroy()
    }

    private fun showBanner(text: String, holdMs: Long = 4000L) {
        welcomeBanner.text = text

        // cancel any pending hide
        bannerHideRunnable?.let { welcomeBanner.removeCallbacks(it) }

        if (welcomeBanner.visibility != View.VISIBLE) {
            welcomeBanner.alpha = 0f
            welcomeBanner.visibility = View.VISIBLE
            welcomeBanner.animate().alpha(1f).setDuration(180).withLayer().start()
        }

        // schedule fade-out
        bannerHideRunnable = Runnable {
            welcomeBanner.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction { welcomeBanner.visibility = View.GONE }
                .withLayer()
                .start()
        }.also { welcomeBanner.postDelayed(it, holdMs) }
    }

    private fun View.pulseThenSetActivated(activateAfter: Boolean, totalMs: Long = 360L) {
        // Play pulse
        background = ContextCompat.getDrawable(context, R.drawable.user_input_pulse)
        (background as? AnimationDrawable)?.start()

        // After pulse, restore selector & set final state
        postDelayed({
            background = ContextCompat.getDrawable(context, R.drawable.user_input_bg_selector)
            isActivated = activateAfter
            isSelected  = activateAfter
        }, totalMs)
    }

    private fun wireEditText(et: EditText) {
        et.background = ContextCompat.getDrawable(this, R.drawable.user_input_bg_selector)
        et.isActivated = et.text?.isNotBlank() == true
        et.isSelected  = et.isActivated

        et.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // 🔹 show the explanation for THIS field
                showBanner(help[et.id] ?: "")
                v.pulseThenSetActivated(et.text.isNotBlank())
            } else {
                val active = et.text.isNotBlank()
                v.isActivated = active
                v.isSelected  = active
            }
        }

        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val active = !s.isNullOrBlank()
                et.isActivated = active
                et.isSelected  = active
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }


    private fun wireSpinner(sp: Spinner, placeholderIndex: Int = 0) {
        sp.background = ContextCompat.getDrawable(this, R.drawable.user_input_bg_selector)

        // Pulse when the user taps to open
        sp.setOnTouchListener { v, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                v.pulseThenSetActivated(sp.selectedItemPosition != placeholderIndex)
            }
            false
        }

        // Lock visual state based on selection
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val active = pos != placeholderIndex
                sp.isActivated = active
                sp.isSelected  = active
            }
            override fun onNothingSelected(p: AdapterView<*>) {
                sp.isActivated = false
                sp.isSelected  = false
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
            R.id.et_confirm_password,
            R.id.et_height,
            R.id.et_weight
        )

        for (id in inputs) {
            findViewById<EditText>(id).setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundResource(
                    if (hasFocus) R.drawable.bg_user_input_selected
                    else R.drawable.bg_user_input
                )
            }
        }
    }

    private companion object {
        const val MIN_HEIGHT_CM = 120f
        const val MAX_HEIGHT_CM = 250f
        const val MIN_WEIGHT_KG = 30f
        const val MAX_WEIGHT_KG = 300f
    }

    private fun isHeightValid(v: Float): Boolean = v in MIN_HEIGHT_CM..MAX_HEIGHT_CM
    private fun isWeightValid(v: Float): Boolean = v in MIN_WEIGHT_KG..MAX_WEIGHT_KG

    /** Limits to given range and decimal places while typing */
    private fun EditText.applyNumericConstraints(min: Float, max: Float, maxDecimals: Int) {
        // Filter: only digits and a single dot
        val digits = "0123456789."
        this.filters = arrayOf(InputFilter { src, _, _, dest, dstart, dend ->
            // block multiple dots
            if (src.contains('.')) {
                if (dest.contains('.')) return@InputFilter ""
                // prevent starting with '.' -> prefix with 0
                if (dstart == 0 && (dest.isEmpty() || dest.toString() == "")) return@InputFilter "0."
            }
            // block non-allowed chars
            if (src.any { it !in digits }) return@InputFilter ""

            // enforce decimal places
            val newTxt = (dest.substring(0, dstart) + src + dest.substring(dend))
            val dotIdx = newTxt.indexOf('.')
            if (dotIdx >= 0 && newTxt.length - dotIdx - 1 > maxDecimals) return@InputFilter ""
            // allow tentative empty/partial states
            return@InputFilter src
        })

        // Real-time range clamp + error
        this.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val v = s?.toString()?.toFloatOrNull()
                error = when {
                    s.isNullOrBlank() -> null
                    v == null -> "Invalid number"
                    v < min -> "Too low (min ${min.toInt()})"
                    v > max -> "Too high (max ${max.toInt()})"
                    else -> null
                }
            }
        })
    }



}
