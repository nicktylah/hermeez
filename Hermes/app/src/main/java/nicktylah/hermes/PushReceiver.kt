package nicktylah.hermes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Base64
import com.github.kittinunf.fuel.httpGet
import com.google.android.mms.InvalidHeaderValueException
import com.google.android.mms.pdu_alt.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.warn
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class PushReceiver : BroadcastReceiver(), AnkoLogger {
    override fun onReceive(context: Context, intent: Intent) {
        // Pushy default code:

//        val notificationTitle = "MyApp"
//        var notificationText = "Test notification"
//
//        // Attempt to extract the "message" property from the payload: {"message":"Hello World!"}
//        if (intent.getStringExtra("message") != null) {
//            notificationText = intent.getStringExtra("message");
//        }
//
//        // Prepare a notification with vibration, sound and lights
//        val builder = NotificationCompat.Builder(context)
//                .setAutoCancel(true)
//                .setSmallIcon(android.R.drawable.ic_dialog_info)
//                .setContentTitle(notificationTitle)
//                .setContentText(content)
//                .setLights(Color.RED, 1000, 1000)
//                .setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, EntryActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT));
//
//        // Automatically configure a Notification Channel for devices running Android O+
//        Pushy.setNotificationChannel(builder, context)
//
//        // Get an instance of the NotificationManager service
//        val notificationManager =  context.getSystemService(NotificationManager::class.java)
//
//        // Build the notification and display it
//        notificationManager.notify(1, builder.build())

        // Pushy default code END

        val recipientIds = intent.getStringExtra("recipientIds")
        val content = intent.getStringExtra("content")
        val attachmentPointer = intent.getStringExtra("attachmentPointer") ?: ""
        val attachmentContentType = intent.getStringExtra("attachmentContentType")

        if (recipientIds == null || recipientIds == "") {
            warn("Invalid message -- no recipient IDs")
            return
        }

        if (content == null && attachmentPointer == "") {
            warn("Invalid message -- no content/attachment")
            return
        }

        val recipientIdStrings = recipientIds.split(",").toTypedArray()
        val recipientIdLongs = recipientIdStrings.map { r ->
            r.toLong()
        }
        val body = content ?: ""

        info("$recipientIdLongs, $body")
        if (attachmentPointer == "" && recipientIdLongs.size == 1) {
            sendSms(recipientIdLongs, body)
        } else {
            if (attachmentPointer != "" && attachmentPointer != "") {
                getAttachmentAndSendMms(
                        recipientIdStrings,
                        body,
                        attachmentPointer,
                        attachmentContentType,
                        context.applicationContext)
            } else {
                sendMmsViaSmsManager(recipientIdStrings, body, context.applicationContext)
            }
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
            attachmentContentType: String?,
            applicationContext: Context
    ) {
        attachmentPointer.httpGet().responseString { _, response, _ ->
            if (response.statusCode != 200) {
                warn("Non 200 Status Code received! Attempting reschedule")
            }

            val attachmentBytes = Base64.decode(response.data, Base64.DEFAULT)
            info("Received attachment: $response")

            val pduBytes = composePdu(phoneNumbers, body, attachmentBytes, attachmentContentType, applicationContext)
            savePduAndSendMms(pduBytes, applicationContext)
        }
    }

    private fun sendMmsViaSmsManager(phoneNumbers: Array<String>, body: String, applicationContext: Context) {
        val pduBytes = composePdu(phoneNumbers, body, byteArrayOf(), null, applicationContext)
        savePduAndSendMms(pduBytes, applicationContext)
    }

    private fun savePduAndSendMms(pdu: ByteArray, applicationContext: Context) {
        val attachment = File(applicationContext.externalCacheDir, UUID.randomUUID().toString())
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
            attachmentContentType: String?,
            applicationContext: Context): ByteArray {
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
