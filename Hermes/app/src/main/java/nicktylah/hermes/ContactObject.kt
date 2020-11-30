package nicktylah.hermes

import android.database.Cursor
import android.provider.ContactsContract
import kotlinx.serialization.*
import kotlinx.serialization.json.JSON

/**
 * Represents a message sender
 */
fun contactObject(cursor: Cursor, phoneNumber: Long): ContactObject {
  val displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME))
  val id = cursor.getLong(cursor.getColumnIndex(ContactsContract.PhoneLookup._ID))
  val photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI))

  return ContactObject(
      displayName,
      id,
      phoneNumber,
      "",
      photoUri
  )
}

@Serializable
class ContactObject(
    val displayName: String,
    val id: Long,
    var phoneNumber: Long,
    var photo: String,
    val photoUri: String?
) {

  fun toJSON(): String? {
    return JSON.stringify(this)
  }

  fun toList(): List<Pair<String, Any?>> {
    return listOf(
        Pair("displayName", this.displayName),
        Pair("id", this.id),
        Pair("phoneNumber", this.phoneNumber),
        Pair("photo", this.photo),
        Pair("photoUri", this.photoUri)
    )
  }

  companion object {
    val projection = arrayOf(
        ContactsContract.PhoneLookup._ID,
        ContactsContract.PhoneLookup.DISPLAY_NAME,
        ContactsContract.PhoneLookup.PHOTO_URI)
  }
}
