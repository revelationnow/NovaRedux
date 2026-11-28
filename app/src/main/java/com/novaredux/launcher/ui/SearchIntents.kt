package com.novaredux.launcher.ui

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.net.toUri
import com.novaredux.launcher.search.SearchResult
import com.novaredux.launcher.search.TypedText

// What a search row hands on, and to which app: a calendar event to the
// calendar (#7); a typed number or address to the dialler, messages, email,
// the browser or a new contact (#9). NovaRedux needs no permission for any.
internal object SearchIntents {

    // At the occurrence search found.
    fun event(event: SearchResult.Event): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)

    fun typed(act: TypedText.Act, text: String): Intent = when (act) {
        TypedText.Act.CALL -> Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", text, null))
        TypedText.Act.MESSAGE -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", text, null))
        TypedText.Act.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", text, null))
        TypedText.Act.OPEN -> Intent(Intent.ACTION_VIEW, TypedText.webUrl(text).toUri())
        TypedText.Act.ADD_CONTACT -> Intent(ContactsContract.Intents.Insert.ACTION)
            .setType(ContactsContract.RawContacts.CONTENT_TYPE)
            .putExtra(
                if ('@' in text) ContactsContract.Intents.Insert.EMAIL else ContactsContract.Intents.Insert.PHONE,
                text
            )
    }
}
