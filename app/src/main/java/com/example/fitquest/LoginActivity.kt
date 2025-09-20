package com.example.fitquest

import android.content.Context // <-- ADDED: Needed for SharedPreferences
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.repository.FitquestRepository
import kotlinx.coroutines.launch


class LoginActivity : AppCompatActivity() {

    private lateinit var repository: FitquestRepository
    private lateinit var pressAnim: android.view.animation.Animation

    // Instance for dataStore
    val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")
    val USER_ID_KEY = intPreferencesKey("LOGGED_IN_USER_ID") // Key for DataStore

    // Async function for storing data using DataStore
    private suspend fun saveUserId(userId: Int) {
        applicationContext.dataStore.edit { settings ->
            settings[USER_ID_KEY] = userId
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)

        // Initialize repository
        repository = FitquestRepository(this)

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


        val edtUsername = findViewById<EditText>(R.id.editUsername)
        val edtPassword = findViewById<EditText>(R.id.editPassword)
        val btnLogin = findViewById<ImageButton>(R.id.btnLogin)

        val mainLayout = findViewById<View>(R.id.loginLayout)

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, insets ->
            val systemInsets = insets.getInsets(Type.systemBars())
            view.setPadding(0, 0, 0, systemInsets.bottom)
            insets
        }

        btnLogin.setOnClickListener {
            it.startAnimation(pressAnim)
            val username = edtUsername.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter username and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("FitquestDB", "Attempting login with username=$username, password=$password")

            lifecycleScope.launch {
                val userId = repository.authenticateUser(username, password)
                if (userId != null) {
                    DataStoreManager.saveUserId(applicationContext, userId)
                    Log.d("FitquestDB", "Login successful. Saved user ID $userId to DataStore.")

                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                    }
                    startActivity(Intent(this@LoginActivity, DashboardActivity::class.java))
                    finish()
                } else {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Invalid username or password.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        }

        val tvSignUpLink = findViewById<TextView>(R.id.tvSignUpLink)
        tvSignUpLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
