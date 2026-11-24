package com.supernova.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.supernova.launcher.search.TypedText

// What a typed number or address is handed to (#9): the dialler, messages,
// email, the browser or a new contact. NovaRedux needs no permission for any.
internal object TypedIntents {

    fun of(act: TypedText.Act, text: String): Intent = when (act) {
        TypedText.Act.CALL -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", text, null))
        TypedText.Act.MESSAGE -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", text, null))
        TypedText.Act.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", text, null))
        TypedText.Act.OPEN -> Intent(Intent.ACTION_VIEW, Uri.parse(TypedText.webUrl(text)))
        TypedText.Act.ADD_CONTACT -> Intent(ContactsContract.Intents.Insert.ACTION)
            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(
                if ('@' in text) ContactsContract.Intents.Insert.EMAIL else ContactsContract.Intents.Insert.PHONE,
                text
            )
    }
}
