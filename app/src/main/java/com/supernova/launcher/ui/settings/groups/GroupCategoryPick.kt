package com.supernova.launcher.ui.settings.groups

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.supernova.launcher.R
import com.supernova.launcher.data.DrawerGroup
import com.supernova.launcher.data.SmartCategory
import com.supernova.launcher.databinding.PageGroupEditBinding
import com.supernova.launcher.ui.LauncherViewModel
import com.supernova.launcher.ui.settings.EnumLabels

// A drawer group page's Apps from a category row, for Nova 7's category
// tabs: None, or one of the categories Smart Folders sort by. With one, the
// group's apps are that category's and keep up as apps install and go;
// picking its apps by hand ends that. The page binds again from what is
// stored.
class GroupCategoryPick(
    private val binding: PageGroupEditBinding,
    private val viewModel: LauncherViewModel
) {
    fun bind(group: DrawerGroup) {
        val context = binding.root.context
        val category = group.category
        binding.groupCategoryValue.text = if (category == null) {
            context.getString(R.string.group_category_none)
        } else {
            context.getString(R.string.group_category_value, context.getString(EnumLabels.of(category)))
        }
    }

    fun pick(group: DrawerGroup) {
        val context = binding.root.context
        val choices = listOf<SmartCategory?>(null) + SmartCategory.entries
        val labels = choices.map { category ->
            if (category == null) context.getString(R.string.option_none) else context.getString(EnumLabels.of(category))
        }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.group_category)
            .setSingleChoiceItems(labels, choices.indexOf(group.category)) { dialog, which ->
                dialog.dismiss()
                if (choices[which] != group.category) viewModel.setGroupCategory(group, choices[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
