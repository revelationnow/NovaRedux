package com.supernova.launcher.ui.settings

import android.content.Context
import com.supernova.launcher.data.LauncherPrefs
import com.supernova.launcher.ui.FontFiles
import com.supernova.launcher.ui.LabelFont

// A label font as its row shows it: a picked font file (#6) by its file's
// name, the others by their own.
internal fun fontLabel(context: Context, font: LabelFont, prefs: LauncherPrefs): String =
    if (font == LabelFont.FILE && prefs.labelFontFile.isNotEmpty()) {
        FontFiles.nameOf(prefs.labelFontFile)
    } else {
        context.getString(EnumLabels.of(font))
    }
