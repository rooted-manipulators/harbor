package app.harbor.ui

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log

/**
 * One person, read out of the row the picker handed back.
 *
 * ## Why this is not `READ_CONTACTS`
 *
 * Harbor needs one name and one number, once, chosen deliberately by the
 * person using it. `READ_CONTACTS` grants something much larger: every
 * contact, at any time, for as long as the permission is held, with no record
 * of which ones were read. Asking for that to save somebody typing ten digits
 * is the wrong trade, and `docs/09-master-context.md` lists it among the
 * permissions not to add without a decision.
 *
 * `ACTION_PICK` against [ContactsContract.CommonDataKinds.Phone] needs no
 * permission at all. The system's own picker runs in its own process, shows
 * the whole address book there, and returns the URI of the single row that
 * was tapped along with a temporary read grant for that row. The address book
 * is never in Harbor's process; one row is, briefly, because it was pointed
 * at. That is the whole difference, and it is why the consent screen a few
 * steps later can go on saying what it says.
 *
 * The phone table rather than the contacts table on purpose: a contact URI
 * names a person but carries no number, and getting one from it means a
 * second query that *does* need the permission. A phone row already has the
 * name, the number and the photo on it.
 */
internal object ContactPick {

    /** What one picked row is worth: everything Harbor asks for on that screen. */
    data class Person(val name: String, val number: String, val photo: String?)

    /**
     * @return the row's name, number and photo URI, or null if the row could
     *   not be read — a grant that expired, or a picker that returned
     *   something with no phone column on it.
     *
     * Never throws. A contacts database is somebody else's process and it is
     * allowed to be strange; a picker that returns nothing useful should leave
     * the person typing the number, not leave them on a crash.
     */
    fun read(context: Context, row: Uri): Person? = runCatching {
        val columns = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        context.contentResolver.query(row, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            Person(
                name = cursor.getString(0).orEmpty().trim(),
                // Spaces, dashes and brackets are how people write numbers down
                // and not how a dialler wants them. A leading + is the one
                // piece of punctuation that carries meaning, so it survives.
                number = cursor.getString(1).orEmpty().filterIndexed { i, c ->
                    c.isDigit() || (c == '+' && i == 0)
                },
                photo = cursor.getString(2),
            )
        }
    }.onFailure { Log.w("ContactPick", "could not read the picked row", it) }.getOrNull()
}
