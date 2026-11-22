package io.github.revelationnow.novaredux.connect

// Who may ask: NovaRedux and its debug build, by package name. Android
// vouches for the name (an app can't borrow another's), so no other app can
// use this one to reach the network.
internal object Callers {
    private val allowed = setOf(
        "io.github.revelationnow.novaredux",
        "io.github.revelationnow.novaredux.debug"
    )

    fun mayAsk(callingPackage: String?): Boolean = callingPackage in allowed
}
