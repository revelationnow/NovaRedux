package com.supernova.launcher.data.imports

import com.supernova.launcher.data.LauncherPrefs
import com.supernova.launcher.data.Pref
import com.supernova.launcher.ui.DesktopSearchBarConfig
import com.supernova.launcher.ui.DockBgStyle
import com.supernova.launcher.ui.FolderAnimation
import com.supernova.launcher.ui.FolderMode
import com.supernova.launcher.ui.FolderScroll
import com.supernova.launcher.ui.LabelConfig
import com.supernova.launcher.ui.LabelContext
import com.supernova.launcher.ui.LabelFont
import com.supernova.launcher.ui.SearchBarConfig
import com.supernova.launcher.ui.SearchBarPlacement
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// How Nova drew icons, labels, the dock, the drawer, folders and the search
// bars, laid over NovaRedux's settings where it has the same choice.
internal object NovaLooks {

    fun applyTo(prefs: LauncherPrefs, nova: NovaValues): LauncherPrefs =
        searchBars(folders(drawer(dock(desktop(prefs, nova), nova), nova), nova), nova)

    private fun desktop(prefs: LauncherPrefs, nova: NovaValues): LauncherPrefs {
        val spec = CellSpec.parse(nova.text("desktop_cellspecs")) ?: return prefs
        return prefs.copy(
            iconScale = spec.iconScale?.let(Pref.iconScale::clamped) ?: prefs.iconScale,
            showLabels = spec.labels ?: prefs.showLabels,
            labelSize = spec.labelSize?.let(Pref.labelSize::clamped) ?: prefs.labelSize,
            labelFont = spec.font ?: prefs.labelFont
        )
    }

    // Nova 7 has a cell spec for the dock; Nova 6 only an icon size in percent.
    private fun dock(prefs: LauncherPrefs, nova: NovaValues): LauncherPrefs {
        val spec = CellSpec.parse(nova.text("dock_cellspecs"))
        val scale = (spec?.iconScale ?: nova.int("dock_icon_size")?.let { it / PERCENT })
            ?.takeIf { differs(it, prefs.iconScale) }
        val styled = nova.bool("dock_styled")
        return prefs.copy(
            dockMatchDesktop = prefs.dockMatchDesktop && scale == null,
            dockIconScale = scale?.let(Pref.dockIconScale::clamped) ?: prefs.dockIconScale,
            dockLabels = spec?.labels ?: prefs.dockLabels,
            dockBgStyle = when (styled) {
                true -> nova.choice("dock_shape", DOCK_SHAPES) ?: DockBgStyle.ROUNDED
                false -> DockBgStyle.NONE
                null -> prefs.dockBgStyle
            },
            dockBgOpacity = nova.int("dock_alpha")?.let { Pref.dockBgOpacity.clamped(it / ALPHA) }
                ?: prefs.dockBgOpacity,
            labelConfig = labels(prefs, LabelContext.DOCK, spec)
        )
    }

    private fun drawer(prefs: LauncherPrefs, nova: NovaValues): LauncherPrefs {
        val spec = CellSpec.parse(nova.text("drawer_cellspecs"))
        val scale = spec?.iconScale?.takeIf { differs(it, prefs.iconScale) }
        return prefs.copy(
            drawerMatchDesktop = prefs.drawerMatchDesktop && scale == null,
            drawerIconScale = scale?.let(Pref.drawerIconScale::clamped) ?: prefs.drawerIconScale,
            drawerLabels = spec?.labels ?: prefs.drawerLabels,
            drawerBgOpacity = nova.int("drawer_transparency")?.let { Pref.drawerBgOpacity.clamped(it / ALPHA) }
                ?: prefs.drawerBgOpacity,
            drawerBgColor = nova.int("drawer_background_color")?.let(::rgb) ?: prefs.drawerBgColor,
            drawerCard = nova.bool("drawer_cards") ?: prefs.drawerCard,
            drawerCardColor = nova.int("drawer_card_color")?.let(::rgb) ?: prefs.drawerCardColor,
            drawerLandColumns = nova.int("drawer_app_grid_land_cols")?.let(Pref.drawerLandColumns::clamped)
                ?: prefs.drawerLandColumns,
            drawerLandRows = nova.int("drawer_app_grid_land_rows")?.let(Pref.drawerLandRows::clamped)
                ?: prefs.drawerLandRows,
            labelConfig = labels(prefs, LabelContext.DRAWER, spec)
        )
    }

    // "WINDOW:172:-1:24:true:true:3:4:VERTICAL": the style first and the
    // scrolling last are what is read.
    private fun folders(prefs: LauncherPrefs, nova: NovaValues): LauncherPrefs {
        val spec = CellSpec.parse(nova.text("folder_cellspecs"))
        val window = nova.text("folder_window_config")?.uppercase(Locale.ROOT)?.split(':').orEmpty()
        return prefs.copy(
            folderIconScale = spec?.iconScale?.let(Pref.folderIconScale::clamped) ?: prefs.folderIconScale,
            folderLabels = spec?.labels ?: prefs.folderLabels,
            folderMode = window.firstOrNull()?.let(FOLDER_MODES::get) ?: prefs.folderMode,
            folderScroll = window.lastOrNull()?.let(FOLDER_SCROLLS::get) ?: prefs.folderScroll,
            folderAnimation = nova.choice("folder_animation", FOLDER_ANIMATIONS) ?: prefs.folderAnimation,
            labelConfig = labels(prefs, LabelContext.FOLDER, spec)
        )
    }

    private fun searchBars(prefs: LauncherPrefs, nova: NovaValues): LauncherPrefs {
        val placement = nova.choice("searchbar_placement", PLACEMENTS)
        val home = BarStyle.parse(nova.text("qsb_style"))
        val inDrawer = BarStyle.parse(nova.text("drawer_search_style"))
        if (placement == null && home == null && inDrawer == null) return prefs
        val config = SearchBarConfig.decode(prefs.searchBarConfig)
        val desktop = config.desktop.copy(
            placement = placement?.name ?: config.desktop.placement,
            radius = home?.radius ?: config.desktop.radius,
            wireframe = home?.wire ?: config.desktop.wireframe
        )
        val drawer = config.drawer.copy(
            radius = inDrawer?.radius ?: config.drawer.radius,
            filled = inDrawer?.wire?.not() ?: config.drawer.filled
        )
        return prefs.copy(searchBarConfig = config.copy(desktop = desktop, drawer = drawer).encode())
    }

    // A surface's own label size and font, where Nova's differ from the
    // desktop's: NovaRedux keeps those as per-surface overrides.
    private fun labels(prefs: LauncherPrefs, context: LabelContext, spec: CellSpec?): String {
        val size = spec?.labelSize?.let(Pref.labelSize::clamped)?.takeIf { it != prefs.labelSize }
        val font = spec?.font?.takeIf { it != prefs.labelFont }
        if (size == null && font == null) return prefs.labelConfig
        val config = LabelConfig.decode(prefs.labelConfig)
        val override = config.stored(context).copy(size = size, font = font?.name, off = false)
        return config.with(context, override).encode()
    }

    private fun differs(scale: Float, desktop: Float): Boolean = abs(scale - desktop) > SAME_SCALE

    private fun rgb(color: Int): String = String.format(Locale.ROOT, "#%06X", color and RGB)

    private const val PERCENT = 100f
    private const val ALPHA = 255f
    private const val SAME_SCALE = 0.01f
    private const val RGB = 0xFFFFFF

    private val DOCK_SHAPES = mapOf(
        "RECT" to DockBgStyle.SQUARE, "RECTANGLE" to DockBgStyle.SQUARE, "ROUNDED" to DockBgStyle.ROUNDED,
        "ROUNDRECT" to DockBgStyle.ROUNDED, "ROUNDED_RECT" to DockBgStyle.ROUNDED,
        "ARC" to DockBgStyle.ARC, "PLATFORM" to DockBgStyle.PLATFORM
    )
    private val FOLDER_MODES = mapOf(
        "WINDOW" to FolderMode.WINDOW, "FULLSCREEN" to FolderMode.IMMERSIVE,
        "FULL_SCREEN" to FolderMode.IMMERSIVE, "IMMERSIVE" to FolderMode.IMMERSIVE
    )
    private val FOLDER_SCROLLS = mapOf("VERTICAL" to FolderScroll.VERTICAL, "HORIZONTAL" to FolderScroll.HORIZONTAL)
    private val FOLDER_ANIMATIONS = mapOf("CIRCLE" to FolderAnimation.CIRCLE, "ZOOM" to FolderAnimation.ZOOM)
    private val PLACEMENTS = SearchBarPlacement.entries.associateBy { it.name } + mapOf(
        "DOCK_BOTTOM" to SearchBarPlacement.DOCK_BELOW, "BELOW_DOCK" to SearchBarPlacement.DOCK_BELOW,
        "DOCK_TOP" to SearchBarPlacement.DOCK_ABOVE, "ABOVE_DOCK" to SearchBarPlacement.DOCK_ABOVE,
        "PAGE_TOP" to SearchBarPlacement.TOP
    )
}

// Nova's icon-and-label spec for one surface,
// "1.25:true:13.0:#40302:false:sans-serif-condensed:true:true": the icon
// scale, whether labels show, the label size in sp and, sixth, the label's
// font family. The fields between aren't read.
internal class CellSpec(val iconScale: Float?, val labels: Boolean?, val labelSize: Int?, val font: LabelFont?) {

    companion object {
        private const val FIELDS = 3
        private const val FONT_FIELD = 5
        private val FONTS = mapOf(
            "" to LabelFont.DEFAULT, "sans-serif" to LabelFont.DEFAULT, "sans-serif-medium" to LabelFont.DEFAULT,
            "sans-serif-light" to LabelFont.DEFAULT, "sans-serif-condensed" to LabelFont.CONDENSED,
            "sans-serif-condensed-light" to LabelFont.CONDENSED, "serif" to LabelFont.SERIF,
            "monospace" to LabelFont.MONOSPACE
        )

        fun parse(text: String?): CellSpec? {
            val parts = text?.split(':')?.takeIf { it.size >= FIELDS } ?: return null
            return CellSpec(
                iconScale = parts[0].toFloatOrNull(),
                labels = parts[1].toBooleanStrictOrNull(),
                labelSize = parts[2].toFloatOrNull()?.roundToInt(),
                font = parts.getOrNull(FONT_FIELD)?.let(FONTS::get)
            )
        }
    }
}

// Nova's style for a search bar, "M_LETTER_MONO:PILLRECT:#ffffffff:56:1":
// the logo, then the bar's shape (or WIRE, an outline), then its colour.
// Only the shape is read; NovaRedux's bar always shows a magnifier.
internal class BarStyle(val radius: Int?, val wire: Boolean?) {

    companion object {
        private const val ROUNDED_RADIUS = 8

        fun parse(text: String?): BarStyle? {
            val shape = text?.split(':')?.getOrNull(1)?.uppercase(Locale.ROOT) ?: return null
            return when (shape) {
                "WIRE" -> BarStyle(radius = null, wire = true)
                "RECT", "RECTANGLE" -> BarStyle(radius = 0, wire = false)
                "ROUNDRECT", "ROUNDED", "ROUNDED_RECT" -> BarStyle(radius = ROUNDED_RADIUS, wire = false)
                "PILLRECT", "PILL", "ROUND" -> BarStyle(radius = DesktopSearchBarConfig.MAX_RADIUS, wire = false)
                else -> null
            }
        }
    }
}
