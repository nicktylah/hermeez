package nicktylah.hermes

import android.database.Cursor
import android.provider.Telephony
import kotlinx.serialization.*
import kotlinx.serialization.json.JSON

/**
 * Represents an sms message
 */
fun smsObject(cursor: Cursor): SmsObject {
  val address = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS))
  val body = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY))
  val date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE))
  val id = cursor.getLong(cursor.getColumnIndex(Telephony.Sms._ID))
  val type = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE))

  return SmsObject(
      address,
      body,
      date,
      id,
      address,
      type
  )
}

@Serializable
class SmsObject(
    val address: String,
    val body: String,
    val date: Long,
    val id: Long,
    val threadId: String?,
    val type: Int
) {

  fun toJSON(): String? {
    return JSON.stringify(this)
  }

  companion object {
    val projection = arrayOf(
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms._ID,
        Telephony.Sms.PERSON,
        Telephony.Sms.THREAD_ID,
        Telephony.Sms.TYPE)
  }
}
