package com.example.fitquest

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.repository.FitquestRepository
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.example.fitquest.database.AppDatabase
import kotlinx.coroutines.withContext


class LoginActivity : AppCompatActivity() {

    private lateinit var repository: FitquestRepository
    private lateinit var pressAnim: android.view.animation.Animation

    private var bgDrawable: SpriteSheetDrawable? = null
    private var bgBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)


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

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)
        repository = FitquestRepository(this)


        val rows = 1
        val cols = 24
        val fps = 12

        val root = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.loginLayout)
        val form = findViewById<android.widget.LinearLayout>(R.id.formContainer)
        val sign = findViewById<android.widget.TextView>(R.id.tvSignUpLink)
        val loginBtn = findViewById<android.widget.ImageButton>(R.id.btnLogin)

        val baseFormBottom = form.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            sign.translationY = ime.bottom.toFloat()
            loginBtn.translationY = ime.bottom.toFloat()

            val extraBottom = maxOf(ime.bottom, sys.bottom)
            form.setPadding(
                form.paddingLeft,
                form.paddingTop,
                form.paddingRight,
                baseFormBottom + extraBottom
            )

            insets
        }

        bgBitmap = BitmapFactory.decodeResource(resources, R.drawable.bg_page_login_spritesheet)

        bgDrawable = bgBitmap?.let { bmp ->
            SpriteSheetDrawable(
                sheet = bmp,
                rows = rows,
                cols = cols,
                fps = fps,
                loop = true,
                scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
            )
        }
        bgDrawable?.let { drawable ->
            root.background = drawable
            root.post { drawable.start() }
        }

        lifecycleScope.launch {
            val existing = DataStoreManager.getUserId(this@LoginActivity).first()
            if (existing != -1) {
                val existsInDb = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    AppDatabase.getInstance(applicationContext).userDAO().getUserById(existing) != null
                }
                if (existsInDb) {
                    startActivity(
                        Intent(this@LoginActivity, DashboardActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    )
                    finish()
                    return@launch
                } else {
                    DataStoreManager.clearUserId(this@LoginActivity)
                }
            }
        }


        val edtUsername = findViewById<EditText>(R.id.editUsername)
        val edtPassword = findViewById<EditText>(R.id.editPassword)
        val btnLogin = findViewById<ImageButton>(R.id.btnLogin)
        val tvSignUpLink = findViewById<TextView>(R.id.tvSignUpLink)



        updateLoginEnabled(btnLogin, edtUsername, edtPassword)

        edtUsername.addTextChangedListener { updateLoginEnabled(btnLogin, edtUsername, edtPassword) }
        edtPassword.addTextChangedListener { updateLoginEnabled(btnLogin, edtUsername, edtPassword) }

        edtPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE &&
                loginFormIsValid(edtUsername.text, edtPassword.text)
            ) {
                btnLogin.performClick(); true
            } else false
        }



        btnLogin.setOnClickListener {
            if (!btnLogin.isEnabled) return@setOnClickListener
            it.startAnimation(pressAnim)

            val username = edtUsername.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            if (!loginFormIsValid(username, password)) return@setOnClickListener

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter username and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("FitquestDB", "Attempting login with username=$username")

            lifecycleScope.launch {
                val userId = repository.authenticateUser(username, password)
                if (userId != null) {
                    DataStoreManager.saveUserId(applicationContext, userId)
                    Log.d("FitquestDB", "Login successful. Saved user ID $userId to DataStore.")
                    Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this@LoginActivity, DashboardActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                    )
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Invalid username or password.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        tvSignUpLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
    override fun onResume() {
        super.onResume()
        bgDrawable?.start()
    }

    override fun onPause() {
        bgDrawable?.stop()
        super.onPause()
    }

    override fun onDestroy() {
        bgDrawable?.stop()
        if (!isChangingConfigurations) {
            bgBitmap?.recycle()
            bgBitmap = null
        }
        super.onDestroy()
    }
    private fun loginFormIsValid(
        u: CharSequence?,
        p: CharSequence?
    ): Boolean {
        val username = u?.toString()?.trim().orEmpty()
        val password = p?.toString().orEmpty()

        return username.isNotEmpty() && password.isNotEmpty()
    }

    private fun updateLoginEnabled(btn: ImageButton, user: EditText, pass: EditText) {
        val enabled = loginFormIsValid(user.text, pass.text)
        btn.isEnabled = enabled
        btn.alpha = if (enabled) 1f else 0.6f
    }

}
