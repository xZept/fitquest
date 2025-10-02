package com.example.fitquest

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.Monster
import com.example.fitquest.database.MonsterListItem
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.shop.PurchaseResult
import com.example.fitquest.shop.ShopRepository
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShopActivity : AppCompatActivity() {

    private lateinit var pressAnim: android.view.animation.Animation
    private lateinit var db: AppDatabase
    private lateinit var repo: ShopRepository
    private var userId: Int = -1

    private var tvCoins: TextView? = null
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: MonsterAdapter

    // In-memory state
    private var balance: Int = 0
    private val items = mutableListOf<MonsterListItem>()

    // Animated background bits
    private var bgBitmap: Bitmap? = null
    private var bgDrawable: SpriteSheetDrawable? = null

    // Coin sprite bits
    private var coinBitmap: Bitmap? = null
    private var coinDrawable: SpriteSheetDrawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        pressAnim = AnimationUtils.loadAnimation(this, R.anim.press)
        db = AppDatabase.getInstance(applicationContext)
        repo = ShopRepository(db)

        tvCoins = findViewById(R.id.tv_coins_badge)
        recycler = findViewById(R.id.shop_items_recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = MonsterAdapter()
        recycler.adapter = adapter

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

        // Load user + ensure wallet + seed and refresh shop
        lifecycleScope.launch {
            userId = DataStoreManager.getUserId(this@ShopActivity).first()
            if (userId != -1) {
                withContext(Dispatchers.IO) { db.userWalletDao().ensure(userId) }
                seedMonstersOnce()
                refreshAll()
            }
        }

        // Apply animated background + coin anim
        applyAnimatedBackground()
        applyCoinBadgeAnimation()
        setupNavigationBar()
    }

    override fun onStart() {
        super.onStart()
        bgDrawable?.start()
        coinDrawable?.start()
    }

    override fun onStop() {
        super.onStop()
        bgDrawable?.stop()
        coinDrawable?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        bgBitmap?.recycle()
        bgBitmap = null
        coinBitmap?.recycle()
        coinBitmap = null
    }

    override fun onResume() {
        super.onResume()
        refreshCoins(alsoRefreshButtons = true)
    }

    /* ---------------- Data ops ---------------- */

    private suspend fun seedMonstersOnce() {
        withContext(Dispatchers.IO) {
            val dao = db.monsterDao()

            // Edit prices/names/sprites here any time
            val catalog = listOf(
                Monster(code = "mushroom", name = "Mushroom", spriteRes = "monster_mushroom", price = 50),
                Monster(code = "goblin",   name = "Goblin",   spriteRes = "monster_goblin",   price = 150)
            )

            // Upsert: insert if missing, else update mutable fields
            catalog.forEach { m ->
                val inserted = dao.insertIgnore(m)
                if (inserted == -1L) {
                    dao.updatePrice(m.code, m.price)
                    dao.updateMeta(m.code, m.name, m.spriteRes)
                }
            }

            // Keep ONLY these in the catalog (optional for dev)
            dao.deleteAllExcept(catalog.map { it.code })
        }
    }

    private fun refreshCoins(alsoRefreshButtons: Boolean = false) {
        val badge = tvCoins ?: return
        if (userId == -1) return
        lifecycleScope.launch(Dispatchers.IO) {
            val coins = repo.getBalance(userId)
            withContext(Dispatchers.Main) {
                balance = coins
                badge.text = coins.toString()
                if (alsoRefreshButtons) adapter.notifyDataSetChanged()
            }
        }
    }

    private fun refreshAll() {
        lifecycleScope.launch(Dispatchers.IO) {
            val coins = repo.getBalance(userId)
            val list = repo.list(userId)
            withContext(Dispatchers.Main) {
                balance = coins
                tvCoins?.text = coins.toString()
                items.clear()
                items.addAll(list)
                adapter.notifyDataSetChanged()
            }
        }
    }

    /* ---------------- Background ---------------- */

    private fun applyCoinBadgeAnimation() {
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inDither = true
        }

        coinBitmap = BitmapFactory.decodeResource(
            resources,
            R.drawable.coin_spritesheet,
            opts
        )

        val coin = SpriteSheetDrawable(
            sheet = requireNotNull(coinBitmap) { "coin_spritesheet failed to decode" },
            rows = 1,
            cols = 6,
            fps  = 12,
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.FIT_CENTER
        )

        findViewById<ImageView>(R.id.iv_coin_anim).setImageDrawable(coin)
        coinDrawable = coin
    }

    private fun applyAnimatedBackground() {
        val opts = BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.RGB_565
            inDither = true
        }

        bgBitmap = BitmapFactory.decodeResource(
            resources,
            R.drawable.bg_shop_spritesheet,
            opts
        )

        val drawable = SpriteSheetDrawable(
            sheet = requireNotNull(bgBitmap) { "bg_shop_spritesheet failed to decode" },
            rows = 1,
            cols = 12,
            fps  = 12,
            loop = true,
            scaleMode = SpriteSheetDrawable.ScaleMode.CENTER_CROP
        )

        findViewById<RelativeLayout>(R.id.shop_layout).background = drawable
        bgDrawable = drawable
    }

    /* ---------------- Nav ---------------- */

    private fun setupNavigationBar() {
        findViewById<ImageView>(R.id.nav_icon_workout).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, WorkoutActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_profile).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, ProfileActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_macro).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, MacroActivity::class.java)); overridePendingTransition(0, 0)
        }
        findViewById<ImageView>(R.id.nav_icon_dashboard).setOnClickListener {
            it.startAnimation(pressAnim)
            startActivity(Intent(this, DashboardActivity::class.java)); overridePendingTransition(0, 0)
        }
    }

    /* ---------------- Adapter ---------------- */

    private inner class MonsterAdapter : RecyclerView.Adapter<MonsterAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val iv: ImageView = v.findViewById(R.id.iv_sprite)
            val name: TextView = v.findViewById(R.id.tv_name)
            val price: TextView = v.findViewById(R.id.tv_price)
            val btn: ImageButton = v.findViewById(R.id.btn_buy)
            val overlay: View = v.findViewById(R.id.overlay_dim)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.item_shop_monster, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]

            val resId = resources.getIdentifier(row.spriteRes, "drawable", packageName)
            holder.iv.setImageResource(if (resId != 0) resId else android.R.color.transparent)

            holder.name.text = row.name
            holder.price.text = "${row.price} coins"

            val locked = row.locked
            val owned = row.owned
            val canBuy = !owned && !locked && row.price <= balance
            val notEnough = !owned && !locked && row.price > balance

            // Button image/state (locked falls back to "not enough" icon if no locked icon exists)
            val lockedIcon = resources.getIdentifier("indicator_locked", "drawable", packageName)
            val imgRes = when {
                owned -> R.drawable.indicator_owned
                locked -> if (lockedIcon != 0) lockedIcon else R.drawable.indicator_not_enough
                canBuy -> R.drawable.button_buy
                else   -> R.drawable.indicator_not_enough
            }
            holder.btn.setImageResource(imgRes)
            holder.btn.isEnabled = canBuy
            holder.btn.isClickable = canBuy

            // Dim overlay for locked or not-enough
            holder.overlay.visibility = if (locked || notEnough) View.VISIBLE else View.GONE

            // A11y
            holder.btn.contentDescription = when {
                owned  -> "${row.name} already owned"
                locked -> "${row.name} is locked. Buy earlier monsters first."
                canBuy -> "Buy ${row.name} for ${row.price} coins"
                else   -> "Not enough coins to buy ${row.name}"
            }

            holder.btn.setOnClickListener {
                if (!canBuy) return@setOnClickListener
                it.startAnimation(pressAnim)
                holder.btn.isEnabled = false

                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { repo.purchase(userId, row.code) }
                    when (result) {
                        is PurchaseResult.Success -> {
                            refreshAll() // recompute locked/owned/affordability
                            Toast.makeText(this@ShopActivity, "Purchased ${row.name}!", Toast.LENGTH_SHORT).show()
                        }
                        is PurchaseResult.Insufficient -> {
                            Toast.makeText(this@ShopActivity, "Not enough coins.", Toast.LENGTH_SHORT).show()
                            notifyItemChanged(position)
                        }
                        is PurchaseResult.AlreadyOwned -> {
                            refreshAll()
                            Toast.makeText(this@ShopActivity, "Already owned.", Toast.LENGTH_SHORT).show()
                        }
                        is PurchaseResult.LockedByProgress -> {
                            Toast.makeText(this@ShopActivity, "Locked. Buy earlier monsters first.", Toast.LENGTH_LONG).show()
                            notifyItemChanged(position)
                        }
                        is PurchaseResult.NotFound -> {
                            Toast.makeText(this@ShopActivity, "Item not found.", Toast.LENGTH_SHORT).show()
                            notifyItemChanged(position)
                        }
                    }
                }
            }
        }
    }
}
