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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.keymouse.launcher.R
import com.keymouse.launcher.model.AppInfo

class AppGridAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onFavoriteToggle: (AppInfo) -> Unit,
    private val onDockToggle: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppGridAdapter.AppViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem.packageName == newItem.packageName
            }

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem == newItem &&
                        oldItem.isFavorite == newItem.isFavorite &&
                        oldItem.isDocked == newItem.isDocked
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_grid, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvAppName)

        fun bind(app: AppInfo) {
            tvName.text = app.label
            ivIcon.setImageDrawable(app.icon)

            itemView.setOnClickListener {
                onAppClick(app)
            }

            // Mouse hover animation
            itemView.setOnHoverListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_HOVER_ENTER -> {
                        v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        if (!v.isFocused) {
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                        }
                    }
                }
                false
            }

            // Keyboard focus animation
            itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }

            // Mouse Right-Click (BUTTON_SECONDARY) handling
            itemView.setOnGenericMotionListener { v, event ->
                if (event.action == MotionEvent.ACTION_BUTTON_PRESS &&
                    event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)
                ) {
                    showContextMenu(v.context, v, app)
                    true
                } else {
                    false
                }
            }

            // Long Press (Touch fallback)
            itemView.setOnLongClickListener { v ->
                showContextMenu(v.context, v, app)
                true
            }
        }

        private fun showContextMenu(context: Context, anchor: View, app: AppInfo) {
            val popup = PopupMenu(context, anchor)
            val favTitle = if (app.isFavorite) context.getString(R.string.unpin_from_favorites) else context.getString(R.string.pin_to_favorites)
            val dockTitle = if (app.isDocked) context.getString(R.string.unpin_from_dock) else context.getString(R.string.pin_to_dock)

            popup.menu.add(0, 1, 0, context.getString(R.string.open_app))
            popup.menu.add(0, 2, 1, favTitle)
            popup.menu.add(0, 3, 2, dockTitle)
            popup.menu.add(0, 4, 3, context.getString(R.string.app_info))
            popup.menu.add(0, 5, 4, context.getString(R.string.uninstall))

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        onAppClick(app)
                        true
                    }
                    2 -> {
                        onFavoriteToggle(app)
                        true
                    }
                    3 -> {
                        onDockToggle(app)
                        true
                    }
                    4 -> {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        true
                    }
                    5 -> {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
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
