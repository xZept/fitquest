package com.example.fitquest.tips

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import com.example.fitquest.R
import com.example.fitquest.datastore.DataStoreManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WarmupTipsDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_FOCUS = "focus"
        fun newInstance(focus: String?): WarmupTipsDialogFragment =
            WarmupTipsDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_FOCUS, focus) }
            }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        isCancelable = false
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.92f).toInt()
            setLayout(width, LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val d = resources.displayMetrics.density
        fun dp(px: Int) = (px * d).toInt()

        fun resolveFirstDrawable(vararg names: String): Int {
            val pkg = ctx.packageName
            for (n in names) {
                val id = resources.getIdentifier(n, "drawable", pkg)
                if (id != 0) return id
            }
            return 0
        }

        val panel = FrameLayout(ctx)

        // Background art
        val ivBg = ImageView(ctx).apply {
            setImageResource(R.drawable.container_handler)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        panel.addView(
            ivBg,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val overlay = FrameLayout(ctx)
        panel.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // ---------- Content (header + pager) ----------
        val contentScroll = ScrollView(ctx).apply { isFillViewport = false }
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(68), dp(24), dp(96)) // top clears ribbon; bottom leaves room for dots+bar
            gravity = Gravity.CENTER_HORIZONTAL
        }
        contentScroll.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        overlay.addView(
            contentScroll,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val tvHeader = TextView(ctx).apply {
            text = "Warm-up Tips"
            setTextColor(Color.BLACK)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        content.addView(tvHeader)

        val pager = ViewPager2(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            clipToPadding = false
        }
        content.addView(pager)

        // ---------- Dots (clean bullets) ----------
        val dots = TabLayout(ctx).apply {
            tabMode = TabLayout.MODE_FIXED
            tabGravity = TabLayout.GRAVITY_CENTER
            setBackgroundColor(Color.TRANSPARENT)
            setSelectedTabIndicatorColor(Color.TRANSPARENT)
            tabRippleColor = null
            setPadding(0, 0, 0, 0)
            setTabTextColors(Color.parseColor("#808080"), Color.BLACK)
            minimumHeight = 0
        }
        overlay.addView(
            dots,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(74) } // ~60 bottom bar + gap
        )

        // ---------- Bottom bar ----------
        val bottomBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
        }
        overlay.addView(
            bottomBar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(60),
                Gravity.BOTTOM
            ).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
                bottomMargin = dp(10)
            }
        )

        // Make checkbox touch area just its own width (no weight)
        val cbDontShow = CheckBox(ctx).apply {
            text = "Don’t show again"
            setTextColor(Color.BLACK)
            // remove any implicit extra padding/min size
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
        }
        bottomBar.addView(
            cbDontShow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // Spacer to push the button to the right (captures the free space instead of the checkbox)
        val spacer = View(ctx)
        bottomBar.addView(
            spacer,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val nextRes = resolveFirstDrawable("button_continue")
        val btnContinue: View =
            if (nextRes != 0) {
                ImageButton(ctx).apply {
                    contentDescription = "Continue"
                    setImageResource(nextRes)
                    background = null
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(56)
                    )
                }
            } else {
                Button(ctx).apply {
                    text = "Continue"
                    isAllCaps = false
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(40)
                    )
                }
            }
        bottomBar.addView(btnContinue)

        // ---------- Data + adapter ----------
        val focus = arguments?.getString(ARG_FOCUS)
        val pages = WarmupTipsContent.forFocus(focus)
        pager.adapter = TipPageAdapter(pages)

        TabLayoutMediator(dots, pager) { tab, _ -> tab.text = "•" }.attach()

        fun styleDotTabs() {
            for (i in 0 until dots.tabCount) {
                val t = dots.getTabAt(i) ?: continue
                val tv = TextView(ctx).apply {
                    text = "•"
                    textSize = 18f
                    setTextColor(if (i == dots.selectedTabPosition) Color.BLACK else Color.parseColor("#808080"))
                }
                t.customView = tv
            }
        }
        styleDotTabs()
        dots.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                (tab.customView as? TextView)?.setTextColor(Color.BLACK)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {
                (tab.customView as? TextView)?.setTextColor(Color.parseColor("#808080"))
            }
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Gate Continue to the last page
        fun updateContinueState() {
            val last = pager.currentItem == ((pager.adapter?.itemCount ?: 1) - 1)
            btnContinue.isEnabled = last
            btnContinue.alpha = if (last) 1f else 0.5f
        }
        updateContinueState()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateContinueState()
        })

        btnContinue.setOnClickListener {
            val last = pager.currentItem == ((pager.adapter?.itemCount ?: 1) - 1)
            if (last) {
                CoroutineScope(Dispatchers.IO).launch {
                    DataStoreManager.setShowWarmupTips(requireContext(), !cbDontShow.isChecked)
                }
                dismissAllowingStateLoss()
            }
        }

        return panel
    }
}
