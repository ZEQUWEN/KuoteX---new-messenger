package com.example.ui.gifts

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * PinnedGiftViewHolder - High-performance ViewHolder for horizontal profile gifts.
 * Uses a CardView container with dynamic clipping, rounded corners (20dp),
 * glowing gradient borders, and animated touch scaling.
 */
class PinnedGiftViewHolder(
    val cardContainer: CardView,
    private val onGiftClick: (PinnedGift) -> Unit
) : RecyclerView.ViewHolder(cardContainer) {

    private val innerContentLayout: LinearLayout
    private val emojiTextView: TextView
    private val titleTextView: TextView
    private val levelBadgeTextView: TextView
    private val rarityBadgeTextView: TextView
    private val starRatingTextView: TextView

    init {
        cardContainer.layoutParams = RecyclerView.LayoutParams(
            dpToPx(130, cardContainer.context),
            dpToPx(165, cardContainer.context)
        ).apply {
            setMargins(
                dpToPx(6, cardContainer.context),
                dpToPx(6, cardContainer.context),
                dpToPx(6, cardContainer.context),
                dpToPx(6, cardContainer.context)
            )
        }

        // Clean container clipping approach
        cardContainer.radius = dpToPx(20, cardContainer.context).toFloat()
        cardContainer.cardElevation = dpToPx(6, cardContainer.context).toFloat()
        cardContainer.maxCardElevation = dpToPx(10, cardContainer.context).toFloat()
        cardContainer.preventCornerOverlap = true
        cardContainer.useCompatPadding = true

        val context = cardContainer.context

        // Inner vertical LinearLayout
        innerContentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(
                dpToPx(8, context),
                dpToPx(10, context),
                dpToPx(8, context),
                dpToPx(8, context)
            )
        }

        // Rarity Tag (top pill)
        rarityBadgeTextView = TextView(context).apply {
            textSize = 10f
            setTextColor(AndroidColor.WHITE)
            gravity = Gravity.CENTER
            setPadding(dpToPx(6, context), dpToPx(2, context), dpToPx(6, context), dpToPx(2, context))
        }
        innerContentLayout.addView(rarityBadgeTextView)

        // Emoji / Lottie visual placeholder
        emojiTextView = TextView(context).apply {
            textSize = 38f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(2, context)
                bottomMargin = dpToPx(2, context)
            }
        }
        innerContentLayout.addView(emojiTextView)

        // Title
        titleTextView = TextView(context).apply {
            textSize = 12f
            maxLines = 1
            setTextColor(AndroidColor.WHITE)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        innerContentLayout.addView(titleTextView)

        // Star Rating (e.g. ★★★)
        starRatingTextView = TextView(context).apply {
            textSize = 11f
            setTextColor(AndroidColor.parseColor("#FFD700"))
            gravity = Gravity.CENTER
        }
        innerContentLayout.addView(starRatingTextView)

        // Level Badge Pill
        levelBadgeTextView = TextView(context).apply {
            textSize = 9f
            setTextColor(AndroidColor.WHITE)
            gravity = Gravity.CENTER
            setPadding(dpToPx(8, context), dpToPx(2, context), dpToPx(8, context), dpToPx(2, context))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(4, context)
            }
        }
        innerContentLayout.addView(levelBadgeTextView)

        cardContainer.addView(innerContentLayout)
    }

    fun bind(gift: PinnedGift) {
        val context = cardContainer.context

        titleTextView.text = gift.title
        emojiTextView.text = gift.emojiIcon
        starRatingTextView.text = gift.upgradeStars

        // Dynamic gradient background and glowing border clipping
        val baseColor = try {
            AndroidColor.parseColor(gift.backdropColorHex)
        } catch (_: Exception) {
            AndroidColor.parseColor("#1E1B4B")
        }

        val glowColor = try {
            AndroidColor.parseColor(gift.accentGlowHex)
        } catch (_: Exception) {
            AndroidColor.parseColor("#8B5CF6")
        }

        val backgroundDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                blendColors(baseColor, glowColor, 0.35f),
                baseColor,
                AndroidColor.parseColor("#0F172A")
            )
        ).apply {
            cornerRadius = dpToPx(20, context).toFloat()
            // Glowing border stroke depending on upgrade level
            val strokeWidth = if (gift.upgradeLevel >= 3) dpToPx(2, context) else dpToPx(1, context)
            setStroke(strokeWidth, glowColor)
        }

        innerContentLayout.background = backgroundDrawable

        // Rarity Pill
        val rarityBg = GradientDrawable().apply {
            cornerRadius = dpToPx(8, context).toFloat()
            setColor(blendColors(glowColor, AndroidColor.BLACK, 0.4f))
        }
        rarityBadgeTextView.background = rarityBg
        rarityBadgeTextView.text = gift.rarityTier.title

        // Level Badge
        val levelBg = GradientDrawable().apply {
            cornerRadius = dpToPx(6, context).toFloat()
            setColor(AndroidColor.parseColor("#334155"))
        }
        levelBadgeTextView.background = levelBg
        levelBadgeTextView.text = "LVL ${gift.upgradeLevel}"

        // Interactive Click with scale bounce
        cardContainer.setOnClickListener {
            val scaleDown = PropertyValuesHolder.ofFloat(View.SCALE_X, 0.92f, 1f)
            val scaleUp = PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.92f, 1f)
            ObjectAnimator.ofPropertyValuesHolder(cardContainer, scaleDown, scaleUp).apply {
                duration = 260
                interpolator = OvershootInterpolator(2.5f)
                start()
            }
            onGiftClick(gift)
        }
    }

    companion object {
        fun create(parent: ViewGroup, onGiftClick: (PinnedGift) -> Unit): PinnedGiftViewHolder {
            val cardView = CardView(parent.context)
            return PinnedGiftViewHolder(cardView, onGiftClick)
        }

        private fun dpToPx(dp: Int, context: Context): Int {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp.toFloat(),
                context.resources.displayMetrics
            ).toInt()
        }

        private fun blendColors(color1: Int, color2: Int, ratio: Float): Int {
            val inverse = 1f - ratio
            val r = (AndroidColor.red(color1) * inverse + AndroidColor.red(color2) * ratio).toInt()
            val g = (AndroidColor.green(color1) * inverse + AndroidColor.green(color2) * ratio).toInt()
            val b = (AndroidColor.blue(color1) * inverse + AndroidColor.blue(color2) * ratio).toInt()
            return AndroidColor.rgb(r, g, b)
        }
    }
}

/**
 * PinnedGiftsAdapter - Recycled ListAdapter for pinned profile header gifts.
 */
class PinnedGiftsAdapter(
    private val onGiftClick: (PinnedGift) -> Unit
) : ListAdapter<PinnedGift, PinnedGiftViewHolder>(PinnedGiftDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PinnedGiftViewHolder {
        return PinnedGiftViewHolder.create(parent, onGiftClick)
    }

    override fun onBindViewHolder(holder: PinnedGiftViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

object PinnedGiftDiffCallback : DiffUtil.ItemCallback<PinnedGift>() {
    override fun areItemsTheSame(oldItem: PinnedGift, newItem: PinnedGift): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: PinnedGift, newItem: PinnedGift): Boolean {
        return oldItem == newItem
    }
}
