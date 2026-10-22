# KeyMouse Launcher 🚀
> An Android Home Launcher built for seamless **Keyboard & Mouse** navigation and multi-app switching.

---

## 🎯 Concept & Design Architecture

Inspired by your wireframe sketches:

```
+-------------------+      +-------------------+      +-------------------+
|  [App] [App] [App]|      |                   |      |                   |
|  [App] [App] [App]|      |     YouTube       |      |    Active App     |
|  [App] [App] [App]|      |   (or any app)    |      |                   |
|                   |      |                   |      |                   |
| [  Pinned Dock  ] |      |        [Floating] |      | [ 🏠 ⬅️ [1][2][3] ] |
+-------------------+      +-------------------+      +-------------------+
    1. Homescreen             2. Any App Open          3. Extended Switcher
 (Portrait & Landscape)      (Persistent Button)      (1-Click Multi-App Bar)
```

1. **Homescreen**:
   - Clean dark glassmorphic design.
   - **Adaptive Grid**: 4 columns in **Portrait**, expanding to 7-8 columns in **Landscape** (DeX / Desktop Mode).
   - **Pinned Taskbar Dock**: Holds favorites with visible slot badges (`1` through `9`).
   - **Instant Type-to-Search**: Start typing any character on your physical keyboard to immediately filter apps.

2. **Floating Button ("Any App Opened")**:
   - Runs via `FloatingSwitcherService` (`SYSTEM_ALERT_WINDOW`).
   - Floats over **any** opened app (YouTube, Chrome, games, documents).
   - Draggable with mouse or touch.
   - Shows badge counter with active/recent apps count.
   - Click or hover to instantly expand the **Extended Apps Switcher**.

3. **Extended Apps Switcher Bar ("Extended Apps Opened")**:
   - Bottom floating dock that slides in over running apps.
   - Displays real-time recent applications (via Android `UsageStatsManager`).
   - Built-in navigation controls: **Home** (returns to launcher), **Back** (via Accessibility Service), **All Apps**, and **Close**.
   - Keyboard quick-switch: press keys `1`–`9` on your physical keyboard to switch immediately!

---

## ⌨️ Keyboard & 🖱️ Mouse Control Reference

| Input Action | Result |
| :--- | :--- |
| **Alt + Tab** | Open and cycle through the Extended Apps Switcher |
| **Number Keys `1` to `9`** | Instantly switch to app slot #1–9 in the taskbar dock |
| **Arrow Keys (Up/Down/Left/Right)** | Navigate apps with prominent cyan glowing focus highlight |
| **Enter / Space** | Open currently selected / focused app |
| **Escape** | Close switcher bar, clear search query, or dismiss popups |
| **Win / Meta / Home Key** | Jump to homescreen / scroll to top of app drawer |
| **Type any letter/digit** | Instant type-to-search without clicking search bar |
| **Mouse Left-Click** | Launch app or trigger dock action |
| **Mouse Right-Click** | Context menu: Pin to Dock, App Info, Uninstall |
| **Mouse Hover** | Dynamic smooth scale (`1.05x`) and card highlight |
| **Mouse Wheel** | Smooth scroll through app drawer and horizontal taskbar |

---

## 📱 Orientation Modes

- **Portrait Mode**: Mobile-friendly single-hand and mouse navigation. Quick access favorites strip + bottom dock.
- **Landscape Mode**: Transforms into a **Samsung DeX / Windows 11 Desktop** interface with a full-width taskbar, Start button, center pinned apps, switcher toggle, and desktop system tray clock/date.

---

## 🛠️ Building & Installing

### Fast One-Click Scripts (PowerShell)

- **Build APK**:
  ```powershell
  .\build.ps1
  ```
  Produces: `app\build\outputs\apk\debug\app-debug.apk`

- **Install to Connected Android Device / Emulator**:
  ```powershell
  .\install.ps1
  ```

### In Android Studio
Open this folder (`launcher`) directly in **Android Studio** and click **Run (Shift+F10)**.

---

## ⚙️ Android Permissions

For the full keyboard & mouse multitasking experience, KeyMouse Launcher uses three permissions (guided inside the built-in **Settings** screen):

1. **Display over other apps (`SYSTEM_ALERT_WINDOW`)**:
   Enables the floating switcher button and bottom dock over active apps.
2. **Usage Access (`PACKAGE_USAGE_STATS`)**:
   Detects active/recent apps to display in the switcher bar.
3. **Accessibility Service (`LauncherAccessibilityService`)**:
   Enables system-level **Back** and **Home** triggers from mouse clicks inside the overlay bar.
