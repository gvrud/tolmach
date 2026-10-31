package ru.tolmach.translator

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * РњРµРЅСЋ РІ РІРёРґРµ В«С…Р»РµР±РЅС‹С… РєСЂРѕС€РµРєВ»: РўРѕР»РјР°С‡ вЂє РСЃС‚РѕСЂРёСЏ РїРµСЂРµРІРѕРґРѕРІ вЂє РЎРїСЂР°РІРєР°.
 * РўРµРєСѓС‰РёР№ РїСѓРЅРєС‚ РІС‹РґРµР»РµРЅ РїРѕР»СѓР¶РёСЂРЅС‹Рј, РѕСЃС‚Р°Р»СЊРЅС‹Рµ вЂ” СЃСЃС‹Р»РєРё.
 */
class BreadcrumbsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    data class Item(
        val label: String,
        val current: Boolean = false,
        val action: (() -> Unit)? = null
    )

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun setItems(items: List<Item>) {
        removeAllViews()
        items.forEachIndexed { index, item ->
            if (index > 0) addView(separator())
            addView(crumb(item))
        }
    }

    private fun crumb(item: Item): TextView = TextView(context).apply {
        text = item.label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(5), dp(6), dp(5), dp(6))
        if (item.current) {
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(attrColor(android.R.attr.textColorPrimary))
        } else {
            setTextColor(attrColor(com.google.android.material.R.attr.colorPrimary))
            isClickable = true
            isFocusable = true
            setOnClickListener { item.action?.invoke() }
        }
    }

    private fun separator(): TextView = TextView(context).apply {
        text = "вЂє"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(attrColor(android.R.attr.textColorSecondary))
        alpha = 0.6f
    }

    private fun attrColor(attr: Int): Int {
        val typed = TypedValue()
        context.theme.resolveAttribute(attr, typed, true)
        return if (typed.resourceId != 0) ContextCompat.getColor(context, typed.resourceId) else typed.data
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

