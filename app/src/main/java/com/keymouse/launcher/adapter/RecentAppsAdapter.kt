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

class RecentAppsAdapter(
    private val onAppSelected: (AppInfo) -> Unit
) : ListAdapter<AppInfo, RecentAppsAdapter.RecentViewHolder>(DIFF_CALLBACK) {

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_app, parent, false)
        return RecentViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecentViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class RecentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivRecentIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvRecentName)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvRecentBadge)

        fun bind(app: AppInfo, position: Int) {
            ivIcon.setImageDrawable(app.icon)
            tvName.text = app.label

            if (position < 9) {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = (position + 1).toString()
            } else {
                tvBadge.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onAppSelected(app)
            }

            itemView.setOnHoverListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_HOVER_ENTER -> {
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
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
                    v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120).start()
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                }
            }
        }
    }
}
