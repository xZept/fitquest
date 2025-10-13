package com.example.fitquest

import android.content.Intent
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
import com.example.fitquest.database.*
import com.example.fitquest.datastore.DataStoreManager
import com.example.fitquest.shop.PurchaseResult
import com.example.fitquest.shop.ShopRepository
import com.example.fitquest.ui.widgets.SpriteSheetDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShopActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHOP_TAB = "shop_tab"           // "monsters" | "items"
        const val TAB_MONSTERS = "monsters"
        const val TAB_ITEMS = "items"
        private const val EDIT_TICKET_CODE = "edit_profile_ticket"
    }

    private lateinit var pressAnim: android.view.animation.Animation
    private lateinit var db: AppDatabase
    private lateinit var repo: ShopRepository
    private var userId: Int = -1

    private var tvCoins: TextView? = null
    private lateinit var recycler: RecyclerView

    // Tabs
    private lateinit var tabMonsters: TextView
    private lateinit var tabItems: TextView
    private var activeTab: String = TAB_MONSTERS

    // Adapters
    private lateinit var monstersAdapter: MonsterAdapter
    private lateinit var itemsAdapter: ItemAdapter

    // State
    private var balance: Int = 0
    private val monsters = mutableListOf<MonsterListItem>()
    private val items = mutableListOf<ItemListItem>()   // from ItemDao

    // Animations using your SpriteSheetDrawable API
    private var bgDrawable: SpriteSheetDrawable? = null
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

        // Tabs
        tabMonsters = findViewById(R.id.tab_monsters)
        tabItems = findViewById(R.id.tab_items)

        monstersAdapter = MonsterAdapter()
        itemsAdapter = ItemAdapter()
        recycler.adapter = monstersAdapter

        tabMonsters.setOnClickListener { if (activeTab != TAB_MONSTERS) switchTab(TAB_MONSTERS) }
        tabItems.setOnClickListener { if (activeTab != TAB_ITEMS) switchTab(TAB_ITEMS) }

        // Immersive (same pattern as your other screens)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }

        // Load user, seed catalogs, open tab, refresh balance
        lifecycleScope.launch {
            userId = DataStoreManager.getUserId(this@ShopActivity).first()
            if (userId != -1) {
                withContext(Dispatchers.IO) { db.userWalletDao().ensure(userId) }
                seedMonstersOnce()
                seedItemsOnce()
                val initialTab = intent?.getStringExtra(EXTRA_SHOP_TAB) ?: TAB_MONSTERS
                switchTab(initialTab)
                refreshCoins()
            }
        }

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
        bgDrawable?.stop()
        coinDrawable?.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // keep balances/buttons fresh when returning from Profile or elsewhere
        refreshCoins(alsoRefreshButtons = true)
        if (activeTab == TAB_ITEMS) refreshItems() else refreshMonsters()
    }

    /* ---------------- Seeding ---------------- */

    private suspend fun seedMonstersOnce() = withContext(Dispatchers.IO) {
        val dao = db.monsterDao()
        val catalog = listOf(
            Monster(code = "mushroom", name = "Mushroom",  spriteRes = "monster_mushroom", price = 75),
            Monster(code = "goblin",   name = "Goblin",    spriteRes = "monster_goblin",   price = 150),
            Monster(code = "ogre",     name = "Ogre",      spriteRes = "monster_ogre",     price = 300),
            Monster(code = "eye",      name = "Giant Eye", spriteRes = "monster_eye",      price = 500)
        )
        catalog.forEach { m ->
            val inserted = dao.insertIgnore(m)
            if (inserted == -1L) {
                dao.updatePrice(m.code, m.price)
                dao.updateMeta(m.code, m.name, m.spriteRes)
            }
        }
        dao.deleteAllExcept(catalog.map { it.code })
    }

    private suspend fun seedItemsOnce() = withContext(Dispatchers.IO) {
        repo.seedItems(
            Item(
                code = EDIT_TICKET_CODE,
                name = "Edit Profile Ticket",
                spriteRes = "ticket_change",
                price = 1000,
                consumable = true,
                category = "ticket",
                description = "Unlock editing your profile once. Consumed when you save."
            )
        )
    }

    /* ---------------- Refreshers ---------------- */

    private fun refreshCoins(alsoRefreshButtons: Boolean = false) {
        val badge = tvCoins ?: return
        if (userId == -1) return
        lifecycleScope.launch(Dispatchers.IO) {
            val coins = repo.getBalance(userId)
            withContext(Dispatchers.Main) {
                balance = coins
                badge.text = coins.toString()
                if (alsoRefreshButtons) {
                    when (activeTab) {
                        TAB_MONSTERS -> monstersAdapter.notifyDataSetChanged()
                        TAB_ITEMS -> itemsAdapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun refreshMonsters() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = db.monsterDao().listForUser(userId)
            withContext(Dispatchers.Main) {
                monsters.clear()
                monsters.addAll(list)
                monstersAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun refreshItems() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = repo.listItemsForUser(userId)
            withContext(Dispatchers.Main) {
                items.clear()
                items.addAll(list)
                itemsAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun switchTab(tab: String) {
        activeTab = if (tab == TAB_ITEMS) TAB_ITEMS else TAB_MONSTERS
        styleTabs()
        recycler.adapter = if (activeTab == TAB_ITEMS) itemsAdapter else monstersAdapter
        if (activeTab == TAB_ITEMS) refreshItems() else refreshMonsters()
    }

    private fun styleTabs() {
        fun TextView.select(sel: Boolean) {
            alpha = if (sel) 1f else 0.6f
            paint.isUnderlineText = sel
            setTextColor(if (sel) Color.WHITE else Color.LTGRAY)
        }
        tabMonsters.select(activeTab == TAB_MONSTERS)
        tabItems.select(activeTab == TAB_ITEMS)
    }

    /* ---------------- Animated BG/coins (matches your API/resources) ---------------- */

    private fun applyCoinBadgeAnimation() {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val sheet = BitmapFactory.decodeResource(resources, R.drawable.coin_spritesheet, opts)
        val coin = SpriteSheetDrawable(
            sheet = sheet,
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
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val sheet = BitmapFactory.decodeResource(resources, R.drawable.bg_page_shop_spritesheet0, opts)
        val drawable = SpriteSheetDrawable(
            sheet = sheet,
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

    /* ---------------- Monster adapter (uses your item_shop_monster.xml ids) ---------------- */

    private inner class MonsterAdapter : RecyclerView.Adapter<MonsterAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val iv: ImageView   = v.findViewById(R.id.iv_sprite)
            val name: TextView  = v.findViewById(R.id.tv_name)
            val price: TextView = v.findViewById(R.id.tv_price)
            val btn: ImageButton = v.findViewById(R.id.btn_buy)
            val overlay: View   = v.findViewById(R.id.overlay_dim)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.item_shop_monster, parent, false)
            return VH(v)
        }

        override fun getItemCount() = monsters.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = monsters[position]

            val resId = resources.getIdentifier(row.spriteRes, "drawable", packageName)
            holder.iv.setImageResource(if (resId != 0) resId else android.R.color.transparent)

            holder.name.text = row.name
            holder.price.text = "${row.price} coins"

            val locked = row.locked
            val owned = row.owned
            val canBuy = !owned && !locked && row.price <= balance
            val notEnough = !owned && !locked && row.price > balance

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
            holder.overlay.visibility = if (locked || notEnough) View.VISIBLE else View.GONE

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
                            refreshCoins()
                            refreshMonsters()
                            Toast.makeText(this@ShopActivity, "Purchased ${row.name}!", Toast.LENGTH_SHORT).show()
                        }
                        is PurchaseResult.Insufficient -> {
                            Toast.makeText(this@ShopActivity, "Not enough coins.", Toast.LENGTH_SHORT).show()
                            notifyItemChanged(position)
                        }
                        is PurchaseResult.AlreadyOwned -> {
                            refreshMonsters()
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

    /* ---------------- Items adapter (new) ---------------- */

    private inner class ItemAdapter : RecyclerView.Adapter<ItemAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val iv: ImageView    = v.findViewById(R.id.iv_sprite)
            val name: TextView   = v.findViewById(R.id.tv_name)
            val price: TextView  = v.findViewById(R.id.tv_price)
            val qty: TextView    = v.findViewById(R.id.tv_owned) // "xN"
            val btn: ImageButton = v.findViewById(R.id.btn_buy)
            val overlay: View    = v.findViewById(R.id.overlay_dim)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.item_shop_item, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val row = items[position]

            val resId = resources.getIdentifier(row.spriteRes, "drawable", packageName)
            holder.iv.setImageResource(if (resId != 0) resId else android.R.color.transparent)

            holder.name.text = row.name
            holder.price.text = "${row.price} coins"

            // qty badge
            if (row.quantity > 0) {
                holder.qty.visibility = View.VISIBLE
                holder.qty.text = "x${row.quantity}"
            } else {
                holder.qty.visibility = View.GONE
            }

            val notEnough = row.price > balance
            holder.overlay.visibility = if (notEnough) View.VISIBLE else View.GONE

            holder.btn.setImageResource(if (notEnough) R.drawable.indicator_not_enough else R.drawable.button_buy)
            holder.btn.isEnabled = !notEnough
            holder.btn.isClickable = !notEnough
            holder.btn.contentDescription = if (notEnough) "Not enough coins" else "Buy ${row.name}"

            holder.btn.setOnClickListener {
                if (notEnough) return@setOnClickListener
                it.startAnimation(pressAnim)
                holder.btn.isEnabled = false

                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { repo.purchaseItem(userId, row.code) }
                    when (result) {
                        is PurchaseResult.Success -> {
                            refreshCoins()
                            refreshItems()
                            Toast.makeText(this@ShopActivity, "Purchased ${row.name}!", Toast.LENGTH_SHORT).show()
                        }
                        is PurchaseResult.Insufficient -> {
                            Toast.makeText(this@ShopActivity, "Not enough coins.", Toast.LENGTH_SHORT).show()
                            notifyItemChanged(position)
                        }
                        else -> {
                            refreshItems()
                            Toast.makeText(this@ShopActivity, "Error purchasing item.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}
