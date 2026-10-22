package com.keymouse.launcher.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.keymouse.launcher.R
import com.keymouse.launcher.model.AppInfo
import java.util.Collections

class ReorderableAppAdapter(
    val isDock: Boolean,
    private val onAppClick: (AppInfo) -> Unit,
    private val onRemove: (AppInfo) -> Unit,
    private val onToggleOther: (AppInfo) -> Unit,
    private val onOrderChanged: (List<String>) -> Unit
) : RecyclerView.Adapter<ReorderableAppAdapter.ViewHolder>() {

    val items = mutableListOf<AppInfo>()

    fun submitList(newItems: List<AppInfo>) {
        items.clear()
        items.addAll(newItems)
        updateShortcutSlots()
        notifyDataSetChanged()
    }

    private fun updateShortcutSlots() {
        if (isDock) {
            items.forEachIndexed { index, app ->
                app.shortcutSlot = if (index < 9) index + 1 else null
            }
        }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size || toPosition >= items.size) return
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        updateShortcutSlots()
        notifyItemMoved(fromPosition, toPosition)
    }

    fun notifyOrderCommitted() {
        updateShortcutSlots()
        notifyDataSetChanged()
        onOrderChanged(items.map { it.packageName })
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes = if (isDock) R.layout.item_dock_app else R.layout.item_recent_app
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView? = itemView.findViewById(R.id.ivDockIcon) ?: itemView.findViewById(R.id.ivRecentIcon)
        private val tvBadge: TextView? = itemView.findViewById(R.id.tvShortcutBadge) ?: itemView.findViewById(R.id.tvRecentBadge)
        private val tvName: TextView? = itemView.findViewById(R.id.tvRecentName)

        fun bind(app: AppInfo, position: Int) {
            ivIcon?.setImageDrawable(app.icon)
            tvName?.text = app.label

            if (isDock && position < 9) {
                tvBadge?.visibility = View.VISIBLE
                tvBadge?.text = (position + 1).toString()
            } else if (!isDock && position < 9) {
                tvBadge?.visibility = View.VISIBLE
                tvBadge?.text = (position + 1).toString()
            } else {
                tvBadge?.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onAppClick(app)
            }

            itemView.setOnLongClickListener { v ->
                showContextMenu(v.context, v, app, bindingAdapterPosition)
                true
            }

            // Mouse Right-Click
            itemView.setOnGenericMotionListener { v, event ->
                if (event.action == MotionEvent.ACTION_BUTTON_PRESS &&
                    event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)
                ) {
                    showContextMenu(v.context, v, app, bindingAdapterPosition)
                    true
                } else {
                    false
                }
            }

            // Mouse hover animation
            itemView.setOnHoverListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_HOVER_ENTER -> {
                        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start()
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        if (!v.isFocused) {
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                        }
                    }
                }
                false
            }

            itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }
        }

        private fun showContextMenu(context: Context, anchor: View, app: AppInfo, currentPos: Int) {
            val popup = PopupMenu(context, anchor)
            val removeTitle = if (isDock) context.getString(R.string.unpin_from_dock) else context.getString(R.string.unpin_from_favorites)
            val otherContainerTitle = if (isDock) {
                if (app.isFavorite) context.getString(R.string.unpin_from_favorites) else context.getString(R.string.pin_to_favorites)
            } else {
                if (app.isDocked) context.getString(R.string.unpin_from_dock) else context.getString(R.string.pin_to_dock)
            }

            popup.menu.add(0, 1, 0, context.getString(R.string.open_app))
            popup.menu.add(0, 2, 1, removeTitle)

            // Reorder options
            if (currentPos > 0) {
                popup.menu.add(0, 3, 2, context.getString(R.string.move_left))
            }
            if (currentPos < items.size - 1) {
                popup.menu.add(0, 4, 3, context.getString(R.string.move_right))
            }

            popup.menu.add(0, 5, 4, otherContainerTitle)
            popup.menu.add(0, 6, 5, context.getString(R.string.app_info))

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        onAppClick(app)
                        true
                    }
                    2 -> {
                        onRemove(app)
                        true
                    }
                    3 -> {
                        // Move left
                        onItemMove(currentPos, currentPos - 1)
                        notifyOrderCommitted()
                        true
                    }
                    4 -> {
                        // Move right
                        onItemMove(currentPos, currentPos + 1)
                        notifyOrderCommitted()
                        true
                    }
                    5 -> {
                        onToggleOther(app)
                        true
                    }
                    6 -> {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }
}
