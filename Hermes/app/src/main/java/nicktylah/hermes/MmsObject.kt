package nicktylah.hermes

import android.database.Cursor
import android.net.Uri
import android.provider.Telephony.Mms

import kotlinx.serialization.*
import kotlinx.serialization.json.JSON

/**
 * Represents an mms message
 */
@Serializable
data class MmsObject(
    var attachment: ByteArray,
    var contentType: String,
    var body: String?,
    var date: Long,
    var id: Long,
    var addresses: List<MmsAddr>,
    var threadId: Long
) {
  // Sets default values, the others require additional queries
  constructor(cursor: Cursor) : this(
      ByteArray(0), // attachment
      "", // contentType
      "", // body
      cursor.getLong(cursor.getColumnIndex(Mms.DATE)) * 1000, // Why are mms dates in seconds?
      cursor.getLong(cursor.getColumnIndex(Mms._ID)),
      emptyList<MmsAddr>(), // addresses
      cursor.getLong(cursor.getColumnIndex(Mms.THREAD_ID))
  )
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MmsObject

    if (!attachment.contentEquals(other.attachment)) return false
    if (contentType != other.contentType) return false
    if (body != other.body) return false
    if (date != other.date) return false
    if (id != other.id) return false
    if (addresses != other.addresses) return false
    if (threadId != other.threadId) return false

    return true
  }

  fun toJSON(): String? {
    return JSON.stringify(this)
  }

  fun toList(): List<Pair<String, Any?>> {
    return listOf(
        Pair("addresses", this.addresses.joinToString(",")),
        // Omitted for brevity
//        Pair("attachment", this.attachment),
        Pair("body", this.body),
        Pair("contentType", this.contentType),
        Pair("date", this.date),
        Pair("id", this.id),
        Pair("threadId", this.threadId)
    )
  }

  override fun hashCode(): Int {
    var result = attachment.hashCode()
    result = 31 * result + contentType.hashCode()
    result = 31 * result + (body?.hashCode() ?: 0)
    result = 31 * result + date.hashCode()
    result = 31 * result + id.hashCode()
    result = 31 * result + addresses.hashCode()
    result = 31 * result + threadId.hashCode()
    return result
  }

  companion object {
    val projection = arrayOf(Mms._ID, Mms.DATE, Mms.THREAD_ID)
    // An outgoing Mms has to be within (now - deadlineMillis) to be considered for submission
    const val deadlineMillis = 30000L
  }
}

data class MmsPart(
    val id: Long,
    val contentType: String,
    val text: String?
) {
  constructor(cursor: Cursor) : this(
      cursor.getLong(cursor.getColumnIndex(Mms.Part._ID)),
      cursor.getString(cursor.getColumnIndex(Mms.Part.CONTENT_TYPE)),
      cursor.getString(cursor.getColumnIndex(Mms.Part.TEXT))
  )
  companion object {
    val contentUri = Uri.parse("content://mms/part")!!
    val projection = arrayOf(Mms.Part._ID, Mms.Part.CONTENT_TYPE, Mms.Part.TEXT)
  }
}

data class MmsAddr(
    val address: String,
    val type: Int,
    var addressLong: Long
) {
  constructor(cursor: Cursor) : this(
      cursor.getString(cursor.getColumnIndex(Mms.Addr.ADDRESS)),
      cursor.getInt(cursor.getColumnIndex(Mms.Addr.TYPE)),
      0L
  )
  companion object {
    val projection = arrayOf(Mms.Addr.ADDRESS, Mms.Addr.TYPE)
    // Trust me, it's from Google. Indicates the sending address
    const val typeFrom = 137
  }
}
