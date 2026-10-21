package io.github.echu2237.sidebar.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import io.github.echu2237.sidebar.R
import io.github.echu2237.sidebar.data.AppEntry
import io.github.echu2237.sidebar.dp

/**
 * Sidebar panel: iPhone-style liquid glass + virtualized app list (ListView, measures only visible rows,
 * avoiding building hundreds of rows at once, which causes main-thread jank and flashing/dropped animation frames).
 * Tap anywhere outside the panel / swipe right on the panel -> [onDismiss]; tap an app row -> [onAppClick].
 */
class PanelView(
    context: Context,
    /** true = horizontal strip along the bottom edge (icons scroll sideways, swipe down dismisses) */
    private val horizontal: Boolean,
    private val onAppClick: (AppEntry) -> Unit,
    private val onDismiss: () -> Unit,
) : FrameLayout(context) {

    private var listView: ListView? = null
    private var hRow: LinearLayout? = null
    private val emptyHint: TextView
    private var apps: List<AppEntry> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = apps.size
        override fun getItem(position: Int): AppEntry = apps[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = true

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView as? AppRow ?: AppRow(context)
            row.bind(getItem(position))
            return row
        }
    }

    init {
        background = LiquidGlassDrawable(dp(28f).toFloat())

        // Native MIUI style: narrow strip panel, icons only, no names
        val root = LinearLayout(context).apply {
            orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            if (horizontal) setPadding(dp(12f), dp(8f), dp(12f), dp(8f))
            else setPadding(dp(8f), dp(12f), dp(8f), dp(12f))
        }

        if (horizontal) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            hRow = row
            val scroller = android.widget.HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = OVER_SCROLL_NEVER
                addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
            root.addView(scroller, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        } else {
            val lv = ListView(context).apply {
                this.adapter = this@PanelView.adapter
                divider = null
                dividerHeight = 0
                setVerticalScrollBarEnabled(false) // a flashing scrollbar is also a source of distraction
                setWillNotDraw(true) // ListView only does layout itself; drawing is left to the child rows
                cacheColorHint = 0x00000000
                overScrollMode = OVER_SCROLL_NEVER
                clipToPadding = false
            }
            listView = lv
            root.addView(
                lv,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }

        emptyHint = TextView(context).apply {
            text = context.getString(R.string.panel_loading)
            setTextColor(TITLE_COLOR)
            alpha = 0.6f
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8f), dp(24f), dp(8f), dp(24f))
        }
        root.addView(
            emptyHint,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setApps(apps: List<AppEntry>) {
        this.apps = apps
        emptyHint.text = context.getString(R.string.panel_empty)
        emptyHint.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        val row = hRow
        if (row != null) {
            row.removeAllViews()
            for (app in apps) {
                row.addView(
                    AppRow(context).also { it.bind(app) },
                    LinearLayout.LayoutParams(dp(60f), ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        } else {
            adapter.notifyDataSetChanged()
        }
    }

    /** Single app row: icon only, centered (native MIUI style), reused via ViewHolder */
    private inner class AppRow(context: Context) : FrameLayout(context) {
        private val icon: ImageView = ImageView(context)

        init {
            // Fixed row height: 52dp icon + 4dp breathing room above and below
            layoutParams = AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(60f),
            )
            isClickable = true
            isFocusable = true
            // Subtle highlighted rounded background behind the icon when pressed
            val pressed = GradientDrawable().apply {
                setColor(0x33FFFFFF.toInt())
                cornerRadius = dp(18f).toFloat()
            }
            val transparent = GradientDrawable().apply { setColor(0x00000000) }
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(), transparent)
            }
            icon.layoutParams = FrameLayout.LayoutParams(
                dp(52f), dp(52f),
                Gravity.CENTER,
            )
            addView(icon)
        }

        fun bind(app: AppEntry) {
            icon.setImageDrawable(app.icon)
            setOnClickListener { onAppClick(app) }
        }
    }

    private var downX = 0f
    private var downY = 0f
    private var swipeConsumed = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onDismiss()
            return true
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                swipeConsumed = false
            }
            MotionEvent.ACTION_MOVE -> {
                // Swiping right off the panel also dismisses it
                val swipe = if (horizontal) ev.rawY - downY else ev.rawX - downX
                if (!swipeConsumed && swipe > dp(56f)) {
                    swipeConsumed = true
                    onDismiss()
                    return true
                }
            }
        }
        // Key: once the gesture is consumed, swallow the rest of its events (including UP),
        // otherwise the row would fire a click on the same gesture's UP -> app launches mid-collapse -> transition captures a half-collapsed panel (flash)
        if (swipeConsumed) return true
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        private val TITLE_COLOR = Color.parseColor("#FF1E293B")
    }
}
