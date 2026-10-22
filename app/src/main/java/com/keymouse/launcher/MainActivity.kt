package com.keymouse.launcher

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keymouse.launcher.adapter.AppGridAdapter
import com.keymouse.launcher.adapter.ReorderableAppAdapter
import com.keymouse.launcher.data.AppRepository
import com.keymouse.launcher.model.AppInfo
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var appRepository: AppRepository

    // Views
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var rvAllApps: RecyclerView
    private lateinit var rvFavorites: RecyclerView
    private lateinit var rvDockApps: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var btnMainSettings: ImageButton

    // Adapters
    private lateinit var appGridAdapter: AppGridAdapter
    private lateinit var favoritesAdapter: ReorderableAppAdapter
    private lateinit var dockAdapter: ReorderableAppAdapter

    // State
    private var allInstalledApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()
    private var favoriteApps: MutableList<AppInfo> = mutableListOf()
    private var dockApps: MutableList<AppInfo> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appRepository = AppRepository.getInstance(this)

        initViews()
        setupAdapters()
        setupDragAndDrop()
        setupListeners()
        loadApplications()
        checkAndStartOverlayService()
    }

    override fun onResume() {
        super.onResume()
        loadApplications()
    }

    private fun initViews() {
        etSearch = findViewById(R.id.etSearchApps)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        rvAllApps = findViewById(R.id.rvAllApps)
        rvFavorites = findViewById(R.id.rvFavorites)
        rvDockApps = findViewById(R.id.rvDockApps)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        btnMainSettings = findViewById(R.id.btnMainSettings)

        updateGridLayout()
    }

    private fun updateGridLayout() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val spanCount = if (isLandscape) 7 else 4
        rvAllApps.layoutManager = GridLayoutManager(this, spanCount)

        rvFavorites.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvDockApps.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setContentView(R.layout.activity_main)
        initViews()
        setupAdapters()
        setupDragAndDrop()
        setupListeners()
        bindData()
    }

    private fun setupAdapters() {
        // Main App Grid Adapter
        appGridAdapter = AppGridAdapter(
            onAppClick = { app ->
                appRepository.launchApp(app.packageName)
            },
            onFavoriteToggle = { app ->
                val newState = !app.isFavorite
                appRepository.setFavorite(app.packageName, newState)
                loadApplications()
            },
            onDockToggle = { app ->
                val newState = !app.isDocked
                appRepository.setDock(app.packageName, newState)
                loadApplications()
            }
        )
        rvAllApps.adapter = appGridAdapter

        // Reorderable Favorites Strip Adapter
        favoritesAdapter = ReorderableAppAdapter(
            isDock = false,
            onAppClick = { app ->
                appRepository.launchApp(app.packageName)
            },
            onRemove = { app ->
                appRepository.setFavorite(app.packageName, false)
                loadApplications()
            },
            onToggleOther = { app ->
                val newState = !app.isDocked
                appRepository.setDock(app.packageName, newState)
                loadApplications()
            },
            onOrderChanged = { newOrderList ->
                appRepository.saveFavoritePackages(newOrderList)
            }
        )
        rvFavorites.adapter = favoritesAdapter

        // Reorderable Bottom Dock Adapter
        dockAdapter = ReorderableAppAdapter(
            isDock = true,
            onAppClick = { app ->
                appRepository.launchApp(app.packageName)
            },
            onRemove = { app ->
                appRepository.setDock(app.packageName, false)
                loadApplications()
            },
            onToggleOther = { app ->
                val newState = !app.isFavorite
                appRepository.setFavorite(app.packageName, newState)
                loadApplications()
            },
            onOrderChanged = { newOrderList ->
                appRepository.saveDockPackages(newOrderList)
            }
        )
        rvDockApps.adapter = dockAdapter
    }

    private fun setupDragAndDrop() {
        // Drag and Drop for Pinned Favorites
        attachReorderHelper(rvFavorites, favoritesAdapter) { newOrder ->
            appRepository.saveFavoritePackages(newOrder)
        }

        // Drag and Drop for Bottom Dock
        attachReorderHelper(rvDockApps, dockAdapter) { newOrder ->
            appRepository.saveDockPackages(newOrder)
        }
    }

    private fun attachReorderHelper(
        recyclerView: RecyclerView,
        adapter: ReorderableAppAdapter,
        onCommitted: (List<String>) -> Unit
    ) {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION) {
                    adapter.onItemMove(from, to)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.animate()?.scaleX(1.15f)?.scaleY(1.15f)?.alpha(0.85f)?.setDuration(120)?.start()
                }
            }

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                viewHolder.itemView.animate().scaleX(1.0f).scaleY(1.0f).alpha(1.0f).setDuration(120)?.start()
                adapter.notifyOrderCommitted()
                onCommitted(adapter.items.map { it.packageName })
            }
        })
        helper.attachToRecyclerView(recyclerView)
    }

    private fun setupListeners() {
        // Instant Live App Search
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {
                btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })

        btnClearSearch.setOnClickListener {
            etSearch.text.clear()
            hideKeyboard()
        }

        btnMainSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun loadApplications() {
        lifecycleScope.launch {
            val apps = appRepository.getInstalledApps()
            allInstalledApps = apps
            val appsMap = apps.associateBy { it.packageName }

            val favPackages = appRepository.getFavoritePackages()
            favoriteApps = favPackages.mapNotNull { appsMap[it] }.toMutableList()

            val dockPackages = appRepository.getDockPackages()
            dockApps = dockPackages.mapNotNull { appsMap[it] }.toMutableList()

            filterApps(etSearch.text.toString())
        }
    }

    private fun filterApps(query: String) {
        val trimmed = query.trim()
        filteredApps = if (trimmed.isEmpty()) {
            allInstalledApps
        } else {
            allInstalledApps.filter {
                it.label.contains(trimmed, ignoreCase = true) ||
                        it.packageName.contains(trimmed, ignoreCase = true)
            }
        }
        bindData()
    }

    private fun bindData() {
        appGridAdapter.submitList(filteredApps)
        favoritesAdapter.submitList(favoriteApps)
        dockAdapter.submitList(dockApps)

        tvEmptyState.visibility = if (filteredApps.isEmpty()) View.VISIBLE else View.GONE
    }

    // Keyboard & Mouse Engine
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode

            // 1. Alt + Tab Shortcut: Open and cycle Recent Apps Switcher
            if (event.isAltPressed && keyCode == KeyEvent.KEYCODE_TAB) {
                triggerOverlaySwitcher()
                return true
            }

            // 2. Number keys (1 to 9): Instant launch app #1 to #9 in dock
            if (!etSearch.isFocused && keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9) {
                val index = keyCode - KeyEvent.KEYCODE_1
                if (index < dockApps.size) {
                    appRepository.launchApp(dockApps[index].packageName)
                    return true
                }
            }

            // 3. Escape key: Clear search or unfocus
            if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
                if (etSearch.text.isNotEmpty()) {
                    etSearch.text.clear()
                    hideKeyboard()
                    return true
                } else if (etSearch.isFocused) {
                    etSearch.clearFocus()
                    rvAllApps.requestFocus()
                    hideKeyboard()
                    return true
                }
            }

            // 4. Windows / Meta / Home key: Scroll to top of app drawer
            if (keyCode == KeyEvent.KEYCODE_WINDOW ||
                keyCode == KeyEvent.KEYCODE_META_LEFT ||
                keyCode == KeyEvent.KEYCODE_META_RIGHT
            ) {
                rvAllApps.smoothScrollToPosition(0)
                rvAllApps.requestFocus()
                return true
            }

            // 5. Instant Type-to-Search: Typing alphanumeric key automatically focuses search
            if (!etSearch.isFocused && !event.isAltPressed && !event.isCtrlPressed) {
                val unicodeChar = event.keyCharacterMap.get(keyCode, event.metaState)
                if (unicodeChar > 31 && Character.isDefined(unicodeChar)) {
                    val charStr = unicodeChar.toChar().toString()
                    etSearch.requestFocus()
                    etSearch.append(charStr)
                    etSearch.setSelection(etSearch.text.length)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun triggerOverlaySwitcher() {
        if (Settings.canDrawOverlays(this)) {
            val serviceIntent = Intent(this, FloatingSwitcherService::class.java).apply {
                action = FloatingSwitcherService.ACTION_SHOW_EXTENDED
            }
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun checkAndStartOverlayService() {
        val prefs = getSharedPreferences(KeyMouseApp.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KeyMouseApp.PREF_FLOATING_ENABLED, true)
        if (enabled && Settings.canDrawOverlays(this)) {
            val serviceIntent = Intent(this, FloatingSwitcherService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        currentFocus?.let {
            imm?.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}
