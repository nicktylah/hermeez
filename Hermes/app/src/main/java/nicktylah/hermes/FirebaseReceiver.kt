package nicktylah.hermes

import android.content.pm.PackageManager
import android.net.Uri
import android.support.v4.app.ActivityCompat
import android.telephony.SmsManager
import android.util.Base64
import com.android.mms.LogTag.warn
import com.github.kittinunf.fuel.httpGet
import com.google.android.mms.InvalidHeaderValueException
import com.google.android.mms.pdu_alt.*
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

/**
 * Listens for new Firebase Cloud Messages, sends sms/mms based on the new data
 */
class FirebaseReceiver : FirebaseMessagingService(), AnkoLogger {

  override fun onCreate() {
    super.onCreate()

    if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) !=
        PackageManager.PERMISSION_GRANTED) {
      info("Permission(s) not granted")
      return
    }
  }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    info("From: " + remoteMessage.from)

    // Check if message contains a data payload.
    if (remoteMessage.data.isEmpty()) {
      warn("FCM Message did not contain the necessary payload items")
      return
    }

    val payload = remoteMessage.data
    info("Message data payload: $payload")

    val recipientIds = payload["recipientIds"]
    val content = payload["content"]
    val attachmentPointer = payload["attachmentPointer"]
    val attachmentContentType = payload["attachmentContentType"]

    if (recipientIds == null) {
      warn("Invalid message -- no recipient IDs")
      return
    }

    if (content == null && attachmentPointer == null) {
      warn("Invalid message -- no content/attachment")
      return
    }

    val recipientIdStrings = recipientIds.split(",").toTypedArray()
    val recipientIdLongs = recipientIdStrings.map { r ->
      r.toLong()
    }
    val body = content ?: ""

    if (attachmentPointer == "" && recipientIdLongs.size == 1) {
      sendSms(recipientIdLongs, body)
    } else {
      if (attachmentPointer != "" && attachmentPointer != null) {
        getAttachmentAndSendMms(
            recipientIdStrings,
            body,
            attachmentPointer,
            attachmentContentType)
      } else {
        sendMmsViaSmsManager(recipientIdStrings, body)
      }
    }

    // Check if message contains a notification payload.
    if (remoteMessage.notification != null) {
      info("Message Notification Body: " + (remoteMessage.notification?.body ?: ""))
    }
  }

  /**
   * Sends an SMS with the provided body to the provided phone number.
   * @param phoneNumbers
   * @param body
   */
  private fun sendSms(phoneNumbers: List<Long>, body: String?) {
    if (phoneNumbers.size > 1) {
      error("Cannot send SMS to multiple recipients")
    }

    try {
      val sms = SmsManager.getDefault()
      val multipartBody = sms.divideMessage(body)
      sms.sendMultipartTextMessage(phoneNumbers[0].toString(), null, multipartBody, null, null)
    } catch (e: Exception) {
      error("Could not send SMS: $e")
    }

  }

  private fun getAttachmentAndSendMms(
      phoneNumbers: Array<String>,
      body: String,
      attachmentPointer: String,
      attachmentContentType: String?
  ) {
    attachmentPointer.httpGet().responseString { request, response, result ->
      if (response.statusCode != 200) {
        warn("Non 200 Status Code received! Attempting reschedule")
      }

      val attachmentBytes = Base64.decode(response.data, Base64.DEFAULT)
      info("Received attachment: $response")

      val pduBytes = composePdu(phoneNumbers, body, attachmentBytes, attachmentContentType)
      savePduAndSendMms(pduBytes)
    }
  }

  private fun sendMmsViaSmsManager(phoneNumbers: Array<String>, body: String) {
    val pduBytes = composePdu(phoneNumbers, body, byteArrayOf(), null)
    savePduAndSendMms(pduBytes)
  }

  private fun savePduAndSendMms(pdu: ByteArray) {
    val attachment = File(externalCacheDir, UUID.randomUUID().toString())
    val outputStream: FileOutputStream
    try {
      outputStream = FileOutputStream(attachment)
      outputStream.write(pdu)
      outputStream.close()
    } catch (e: IOException) {
      e.printStackTrace()
    }

    val sms = SmsManager.getDefault()
    try {
      sms.sendMultimediaMessage(applicationContext, Uri.fromFile(attachment), null, null, null)
    } catch (e: IllegalArgumentException) {
      error("Could not send MMS: $e")
    }

  }

  private fun composePdu(
      phoneNumbers: Array<String>,
      body: String,
      attachmentBytes: ByteArray,
      attachmentContentType: String?): ByteArray {
    val sendRequest = SendReq()
    // TODO: Get own phone number properly
    val ownPhoneNumber = 0L
    sendRequest.from = EncodedStringValue(ownPhoneNumber.toString())
    try {
      sendRequest.messageType = 128
      sendRequest.mmsVersion = 18
    } catch (e: InvalidHeaderValueException) {
      e.printStackTrace()
    }

    for (phoneNumber in phoneNumbers) {
      val recipient = EncodedStringValue.extract(phoneNumber)
      sendRequest.addTo(recipient[0])
    }

    val pduBody = PduBody()

    // Send the text part, if it exists
    if (body != "") {
      val bodyPdu = PduPart()
      bodyPdu.name = "body".toByteArray()
      bodyPdu.contentType = "text/plain".toByteArray()
      bodyPdu.data = body.toByteArray()
      pduBody.addPart(bodyPdu)
    }

    // Send the attachment part, if it exists
    if (attachmentBytes.isNotEmpty()) {
      val attachmentPdu = PduPart()
      attachmentPdu.name = "attachment".toByteArray()
      attachmentPdu.contentType = attachmentContentType!!.toByteArray()
      attachmentPdu.data = attachmentBytes
      pduBody.addPart(attachmentPdu)
    }

    sendRequest.body = pduBody
    return PduComposer(applicationContext, sendRequest).make()
  }
}

