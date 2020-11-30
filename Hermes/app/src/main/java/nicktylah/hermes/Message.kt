package nicktylah.hermes

import kotlinx.serialization.*
import kotlinx.serialization.json.JSON

/**
 * Represents an sms/mms message
 */
@Serializable
data class Message(
    val addresses: Set<Long>,
    val attachment: String?,
    val body: String?,
    val contentType: String?,
    val date: Long,
    val sender: Long,
    val threadId: Long,
    val type: Int
) {

  fun toJSON(): String? {
    return JSON.stringify(this)
  }

  fun toList(): List<Pair<String, Any?>> {
    return listOf(
        Pair("addresses", this.addresses),
        Pair("attachment", this.attachment),
        Pair("body", this.body),
        Pair("contentType", this.contentType),
        Pair("date", this.date),
        Pair("sender", this.sender),
        Pair("threadId", this.threadId),
        Pair("type", this.type)
    )
  }
}