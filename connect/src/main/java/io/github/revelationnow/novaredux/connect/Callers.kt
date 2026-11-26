package io.github.revelationnow.novaredux.connect

import android.content.Context
import android.content.pm.PackageManager

// Who may ask: NovaRedux and its debug build, by package name, and only as
// signed with this app's own key. Android vouches for the name (an app
// can't borrow another's while that one is installed); the key rules out
// another app taking the name where NovaRedux isn't installed.
internal object Callers {
    private val allowed = setOf(
        "io.github.revelationnow.novaredux",
        "io.github.revelationnow.novaredux.debug"
    )

    fun named(callingPackage: String?): Boolean = callingPackage in allowed

    fun mayAsk(context: Context, callingPackage: String?): Boolean =
        callingPackage != null && named(callingPackage) && signedAlike(context, callingPackage)

    private fun signedAlike(context: Context, other: String): Boolean =
        context.packageManager.checkSignatures(context.packageName, other) == PackageManager.SIGNATURE_MATCH
}
