package com.keymouse.launcher.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.keymouse.launcher.R
import com.keymouse.launcher.model.AppInfo

class DockAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: ((AppInfo, View) -> Unit)? = null
) : ListAdapter<AppInfo, DockAdapter.DockViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem.packageName == newItem.packageName
            }

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DockViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dock_app, parent, false)
        return DockViewHolder(view)
    }

    override fun onBindViewHolder(holder: DockViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class DockViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivDockIcon)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvShortcutBadge)

        fun bind(app: AppInfo, position: Int) {
            ivIcon.setImageDrawable(app.icon)

            // Number badge for 1-9 shortcuts
            if (position < 9) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = (position + 1).toString()
            } else {
                tvBadge.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onAppClick(app)
            }

            itemView.setOnLongClickListener { v ->
                onAppLongClick?.invoke(app, v)
                true
            }

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
    }
}
