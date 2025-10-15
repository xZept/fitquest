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
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.getkeepsafe.taptargetview.TapTargetView



class ShopActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHOP_TAB = "shop_tab"           // "monsters" | "items"
        const val TAB_MONSTERS = "monsters"
        const val TAB_ITEMS = "items"
        private const val EDIT_TICKET_CODE = "edit_profile_ticket"

        private const val TOUR_PREFS = "onboarding"
        private const val SHOP_TOUR_DONE_KEY_PREFIX = "shop_tour_done_v1_u_" // per-user key
        // per-process guard (per user)
        private val shopTourShownUsersThisProcess = mutableSetOf<Int>()
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

        // Load user, seed catalogs, open tab, refresh balance + BG
        lifecycleScope.launch {
            userId = DataStoreManager.getUserId(this@ShopActivity).first()
            if (userId != -1) {
                withContext(Dispatchers.IO) { db.userWalletDao().ensure(userId) }
                seedMonstersOnce()
                seedItemsOnce() // includes tickets + backgrounds
                val initialTab = intent?.getStringExtra(EXTRA_SHOP_TAB) ?: TAB_MONSTERS
                switchTab(initialTab)
                refreshCoins()
                updateShopBackground() // apply owned tier (if any)
            }
        }

        applyAnimatedBackground(tier = 0) // default until we know user
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

        lifecycleScope.launch {
            // make sure we have the current user id *now*
            userId = DataStoreManager.getUserId(this@ShopActivity).first()

            // keep balances/buttons fresh
            refreshCoins(alsoRefreshButtons = true)
            if (activeTab == TAB_ITEMS) refreshItems() else refreshMonsters()
            updateShopBackground()

            if (userId > 0) showShopTourIfNeeded(userId)
        }
    }


    /* ---------------- Shop Tour ---------------- */

    private fun TapTarget.applyShopTourStyle(): TapTarget = apply {
        // 80% white scrim + black text (same as your other tours)
        dimColor(R.color.tour_white_80)      // 80% white in colors.xml (#CCFFFFFF)

        // Make BOTH texts the same bright color
        titleTextColor(R.color.tour_orange)   // or android.R.color.black
        descriptionTextColor(R.color.tour_orange)

        // Ring/target styling
        outerCircleColor(R.color.white) // subtle ring, then use alpha below
        outerCircleAlpha(0.12f)              // keep ring faint over light scrim
        targetCircleColor(R.color.white)

        tintTarget(true)
        transparentTarget(true)
        cancelable(true)
        drawShadow(false)
    }



    private fun showShopTourIfNeeded(userId: Int) {
        if (userId <= 0) return

        val prefs = getSharedPreferences(TOUR_PREFS, MODE_PRIVATE)
        val userDoneKey = "$SHOP_TOUR_DONE_KEY_PREFIX$userId"

        // DEV ONLY: force-show while testing a specific user
        if (BuildConfig.DEBUG) {
            // Uncomment while testing:
            // prefs.edit().remove(userDoneKey).apply()
            // shopTourShownUsersThisProcess.remove(userId)
        }

        // per-process (per user) + persisted (per user) guards
        if (shopTourShownUsersThisProcess.contains(userId) || prefs.getBoolean(userDoneKey, false)) return

        val coins    = findViewById<View>(R.id.tv_coins_badge)
        val tabMons  = findViewById<View>(R.id.tab_monsters)
        val root     = findViewById<View>(R.id.shop_layout)

        root.post {
            // mark as shown for this user in this process
            shopTourShownUsersThisProcess.add(userId)

            val firstTargets = mutableListOf<TapTarget>().apply {
                coins?.let {
                    add(
                        TapTarget.forView(it, "Your balance for buying monsters and items.", "")
                            .applyShopTourStyle()
                    )
                }
                tabMons?.let {
                    add(
                        TapTarget.forView(it, "Collect monsters. Some unlock after earlier ones.", "")
                            .applyShopTourStyle()
                    )
                }
            }

            if (firstTargets.isEmpty()) {
                finishShopTour(prefs, userDoneKey)
                return@post
            }

            TapTargetSequence(this)
                .targets(firstTargets)
                .listener(object : TapTargetSequence.Listener {
                    override fun onSequenceFinish() {
                        // After intro, highlight first MONSTER item’s Buy button (if visible),
                        // then switch to Items and do the same there.
                        showFirstListItemThenItemsStage(prefs, userDoneKey)
                    }
                    override fun onSequenceStep(lastTarget: TapTarget, targetClicked: Boolean) {}
                    override fun onSequenceCanceled(lastTarget: TapTarget) {
                        prefs.edit().putBoolean(userDoneKey, true).apply()
                    }
                })
                .start()
        }
    }


    private fun showFirstListItemThenItemsStage(
        prefs: android.content.SharedPreferences,
        userDoneKey: String
    ) {
        waitForRecyclerPopulation {
            val btn = findFirstBuyButtonInRecycler()
            if (btn != null) {
                TapTargetView.showFor(
                    this,
                    TapTarget.forView(btn, "Tap to purchase monster if you have enough coins.", "")
                        .applyShopTourStyle(),
                    object : TapTargetView.Listener() {
                        override fun onTargetDismissed(view: TapTargetView?, userInitiated: Boolean) {
                            showItemsStage(prefs, userDoneKey)
                        }
                    }
                )
            } else {
                showItemsStage(prefs, userDoneKey)
            }
        }
    }

    private fun showItemsStage(
        prefs: android.content.SharedPreferences,
        userDoneKey: String
    ) {
        switchTab(TAB_ITEMS)
        val itemsTab = findViewById<View>(R.id.tab_items)

        TapTargetView.showFor(
            this,
            TapTarget.forView(itemsTab, "Buy tickets and background upgrades here.", "")
                .applyShopTourStyle(),
            object : TapTargetView.Listener() {
                override fun onTargetDismissed(view: TapTargetView?, userInitiated: Boolean) {
                    waitForRecyclerPopulation {
                        val btn = findFirstBuyButtonInRecycler()
                        if (btn != null) {
                            TapTargetView.showFor(
                                this@ShopActivity,
                                TapTarget.forView(btn, "Purchase tickets or background tiers.", "")
                                    .applyShopTourStyle(),
                                object : TapTargetView.Listener() {
                                    override fun onTargetDismissed(v: TapTargetView?, userInitiated: Boolean) {
                                        finishShopTour(prefs, userDoneKey)
                                    }
                                }
                            )
                        } else {
                            finishShopTour(prefs, userDoneKey)
                        }
                    }
                }
            }
        )
    }

    private fun finishShopTour(
        prefs: android.content.SharedPreferences,
        userDoneKey: String
    ) {
        prefs.edit().putBoolean(userDoneKey, true).apply()
    }



    /** Wait until the RecyclerView has at least one child (bound view), with a few retries. */
    private fun waitForRecyclerPopulation(maxTries: Int = 10, delayMs: Long = 140, onReady: () -> Unit) {
        fun check(tries: Int) {
            if (recycler.childCount > 0) {
                onReady()
            } else if (tries < maxTries) {
                recycler.postDelayed({ check(tries + 1) }, delayMs)
            } else {
                onReady() // give up gracefully
            }
        }
        check(0)
    }

    /** Finds the first visible item’s Buy button inside the RecyclerView, if present. */
    private fun findFirstBuyButtonInRecycler(): View? {
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i) ?: continue
            val btn = child.findViewById<ImageButton?>(R.id.btn_buy)
            if (btn != null && btn.visibility == View.VISIBLE) return btn
        }
        return null
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

    /**
     * Seeds: edit ticket + all background tiers for profile/shop/quest.
     * Uses a simple pricing curve (tier * 60).
     */
    private suspend fun seedItemsOnce() = withContext(Dispatchers.IO) {
        val all = mutableListOf<Item>()

        // 1) Edit ticket (consumable)
        all += Item(
            code = EDIT_TICKET_CODE,
            name = "Edit Profile Ticket",
            spriteRes = "ticket_change",
            price = 10,
            consumable = true,
            category = "ticket",
            description = "Unlock editing your profile once. Consumed when you save."
        )

        fun bgItem(page: String, tier: Int, spriteRes: String, nice: String) = Item(
            code = "bg_${page}_tier_${tier}",
            name = "$nice BG — Tier $tier",
            spriteRes = spriteRes,
            price = tier * 1, // tweak to taste
            consumable = false,
            category = "bg_$page",
            description = "Upgrade the $nice page background to tier $tier."
        )

        // 2) Backgrounds: Profile (uses bg_page_profile_spritesheetN)
        for (t in 1..6) {
            all += bgItem("profile", t, "bg_page_profile_spritesheet$t", "Profile")
        }
        // 3) Backgrounds: Shop (uses bg_page_shop_spritesheetN)
        for (t in 1..6) {
            all += bgItem("shop", t, "bg_page_shop_spritesheet$t", "Shop")
        }
        // 4) Backgrounds: Quest/Preview (uses dashboard sheet names per your note)
        for (t in 1..6) {
            all += bgItem("quest", t, "bg_page_dashboard_spritesheet$t", "Quest")
        }

        repo.seedItems(*all.toTypedArray())
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

    /* ---------------- Animated BG/coins ---------------- */

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

    private fun applyAnimatedBackground(tier: Int) {
        // Tier 0 is default
        val resName = if (tier <= 0) "bg_page_shop_spritesheet0" else "bg_page_shop_spritesheet$tier"
        val resId = resources.getIdentifier(resName, "drawable", packageName)
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val sheet = BitmapFactory.decodeResource(resources, if (resId != 0) resId else R.drawable.bg_page_shop_spritesheet0, opts)
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
        bgDrawable?.start()
    }

    private fun updateShopBackground() {
        if (userId == -1) return
        lifecycleScope.launch(Dispatchers.IO) {
            val tier = repo.getHighestBackgroundTier(userId, "shop")
            withContext(Dispatchers.Main) { applyAnimatedBackground(tier) }
        }
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

    /* ---------------- Monster adapter ---------------- */

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

    /* ---------------- Items adapter (tickets + backgrounds) ---------------- */

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

            val isBackground = row.code.startsWith("bg_")
            val ownedQty = row.quantity

            // qty badge (hide for backgrounds; they are 1x)
            if (!isBackground && ownedQty > 0) {
                holder.qty.visibility = View.VISIBLE
                holder.qty.text = "x${ownedQty}"
            } else {
                holder.qty.visibility = View.GONE
            }

            // Set a safe default; we’ll refine once we know lock/owned states
            holder.overlay.visibility = View.GONE
            holder.btn.isEnabled = false
            holder.btn.isClickable = false
            holder.btn.setImageResource(R.drawable.indicator_not_enough)

            // Resolve lock/owned/enough
            lifecycleScope.launch(Dispatchers.IO) {
                val locked = if (isBackground) repo.isBackgroundLocked(userId, row.code) else false
                val owned = isBackground && ownedQty > 0
                val notEnough = row.price > balance
                val canBuy = !owned && !locked && !notEnough

                withContext(Dispatchers.Main) {
                    val imgRes = when {
                        owned -> R.drawable.indicator_owned
                        locked -> {
                            val id = resources.getIdentifier("indicator_locked", "drawable", packageName)
                            if (id != 0) id else R.drawable.indicator_not_enough
                        }
                        notEnough -> R.drawable.indicator_not_enough
                        else -> R.drawable.button_buy
                    }
                    holder.btn.setImageResource(imgRes)
                    holder.overlay.visibility = if (locked || notEnough) View.VISIBLE else View.GONE
                    holder.btn.isEnabled = canBuy
                    holder.btn.isClickable = canBuy

                    holder.btn.contentDescription = when {
                        owned -> "${row.name} already owned"
                        locked -> "Locked. Buy the previous tier first."
                        notEnough -> "Not enough coins"
                        else -> "Buy ${row.name}"
                    }

                    holder.btn.setOnClickListener {
                        if (!canBuy) return@setOnClickListener
                        it.startAnimation(pressAnim)
                        holder.btn.isEnabled = false

                        lifecycleScope.launch {
                            val result = withContext(Dispatchers.IO) { repo.purchaseItem(userId, row.code) }
                            when (result) {
                                is PurchaseResult.Success -> {
                                    refreshCoins()
                                    refreshItems()
                                    Toast.makeText(this@ShopActivity, "Purchased ${row.name}!", Toast.LENGTH_SHORT).show()

                                    // If they bought a shop BG, apply immediately
                                    if (row.code.startsWith("bg_shop_")) updateShopBackground()
                                }
                                is PurchaseResult.Insufficient -> {
                                    Toast.makeText(this@ShopActivity, "Not enough coins.", Toast.LENGTH_SHORT).show()
                                    notifyItemChanged(position)
                                }
                                is PurchaseResult.LockedByProgress -> {
                                    Toast.makeText(this@ShopActivity, "Locked. Buy the previous tier first.", Toast.LENGTH_LONG).show()
                                    notifyItemChanged(position)
                                }
                                is PurchaseResult.AlreadyOwned -> {
                                    refreshItems()
                                    Toast.makeText(this@ShopActivity, "Already owned.", Toast.LENGTH_SHORT).show()
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
    }


}
