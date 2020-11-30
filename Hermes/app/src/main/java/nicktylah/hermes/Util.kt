package nicktylah.hermes

import android.app.job.JobInfo
import android.app.job.JobInfo.BACKOFF_POLICY_EXPONENTIAL
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Telephony.*
import android.support.annotation.RequiresApi
import android.util.Base64
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JSON
import kotlinx.serialization.toUtf8Bytes
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.debug
import org.jetbrains.anko.info
import org.jetbrains.anko.warn
import java.io.File
import java.io.FileWriter
import java.lang.ClassCastException
import java.time.LocalDateTime
import java.util.*


object Util : AnkoLogger {

  private const val outgoingMmsSmsJobId = 1
  private const val contactUpdateDeadlineMillis = 0L
//  private const val contactUpdateDeadlineMillis = 3600000L // 1 hour (DEBUG)
//  private const val contactUpdateDeadlineMillis = 1209600000L // 14 days
  private const val hermesUrl = "https://${BuildConfig.HERMES_HOST}"
  private const val postV1MessageEndpoint = "$hermesUrl/v1/messages"
  private const val postV1FcmTokenEndpoint = "$hermesUrl/v1/token"
  private const val contactKey = "contacts"
  private const val imageKey = "images"

//  val outgoingJobDeadline = 10000L
//  val millisToWaitBeforeJob = 3000L

  fun scheduleMmsSmsJob(context: Context) {
    val serviceComponent = ComponentName(context, MmsSmsJobService::class.java)
    val outgoingSmsJob = JobInfo.Builder(outgoingMmsSmsJobId, serviceComponent)
        .addTriggerContentUri(JobInfo.TriggerContentUri(
            MmsSms.CONTENT_URI,
            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
//            .setOverrideDeadline(outgoingJobDeadline)
        .setBackoffCriteria(10000L, BACKOFF_POLICY_EXPONENTIAL)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        .setTriggerContentMaxDelay(1000L)
//        .setPersisted(true)
        .build()

    val jobScheduler = context.getSystemService(JobScheduler::class.java)
    jobScheduler?.schedule(outgoingSmsJob)
  }

  fun cancelMmsSmsJob(context: Context) {
    val jobScheduler = context.getSystemService(JobScheduler::class.java)
    jobScheduler?.cancel(outgoingMmsSmsJobId)
  }

  fun getLastSmsId(context: Context): Long {
    val sharedPrefs = context.getSharedPreferences("hermes_shared_preferences", Context.MODE_PRIVATE)
    return try {
      sharedPrefs.getLong("last_sms_id", 0L)
    } catch (e: ClassCastException) {
      val intSmsId = sharedPrefs.getInt("last_sms_id", 0)
      intSmsId.toLong()
    }
  }

  fun setLastSmsId(context: Context, lastSmsId: Long): Boolean {
    val sharedPrefs = context.getSharedPreferences("hermes_shared_preferences", Context.MODE_PRIVATE)
    return sharedPrefs.edit().putLong("last_sms_id", lastSmsId).commit()
  }

  fun getLastMmsId(context: Context): Long {
    val sharedPrefs = context.getSharedPreferences("hermes_shared_preferences", Context.MODE_PRIVATE)
    return sharedPrefs.getLong("last_mms_id", 0L)
  }

  fun setLastMmsId(context: Context, lastMmsId: Long): Boolean {
    val sharedPrefs = context.getSharedPreferences("hermes_shared_preferences", Context.MODE_PRIVATE)
    return sharedPrefs.edit().putLong("last_mms_id", lastMmsId).commit()
  }

  fun getOwnPhoneNumber(context: Context): Long {
    val sharedPrefs = context.getSharedPreferences("hermes_shared_preferences", Context.MODE_PRIVATE)
    return sharedPrefs.getLong("own_phone_number", 0L)
  }

  fun setOwnPhoneNumber(context: Context, ownPhoneNumber: Long): Boolean {
    val sharedPrefs = context.getSharedPreferences("hermes_shared_preferences", Context.MODE_PRIVATE)
    return sharedPrefs.edit().putLong("own_phone_number", ownPhoneNumber).commit()
  }

  fun getFcmToken(context: Context): String {
    val sharedPrefs = context.getSharedPreferences("hermes_shared_preferences", Context.MODE_PRIVATE)
    return sharedPrefs.getString("fcm_token", "")
  }

  fun setPushyToken(context: Context, token: String): Boolean {
    val sharedPrefs = context.getSharedPreferences("hermes_shared_preferences", Context.MODE_PRIVATE)
    return sharedPrefs.edit().putString("fcm_token", token).commit()
  }

  // TODO: Encrypt
  fun storeContact(context: Context, contact: ContactObject) {
    // Determine if we should re-submit this contact photo
    val sharedPrefs = context.getSharedPreferences("hermes_shared_preferences", Context.MODE_PRIVATE)
    val lastUpdated = sharedPrefs.getLong(contact.phoneNumber.toString(), 0L)
    val now = System.currentTimeMillis()
    if (lastUpdated > (now - contactUpdateDeadlineMillis)) {
      info("Contact was last updated within last ${contactUpdateDeadlineMillis/3600000} hours. Skipping.")
      return
    }

    val user = FirebaseAuth.getInstance().currentUser
    if (user == null) {
      warn("Null Firebase user, cannot auth this contact upload")
      return
    }
    val storageReference = FirebaseStorage.getInstance()
        .getReference(user.uid)
        .child(contactKey)
    info("storageRef: ${storageReference.bucket}, ${storageReference.path}\nuser: ${user.uid}")
    val attachmentReference = storageReference.child("${contact.phoneNumber}")
    val contactJSON = contact.toJSON()
    if (contactJSON == null) {
      warn("Null contact JSON")
      return
    }

    val uploadTask = attachmentReference.putBytes(contactJSON.toUtf8Bytes())
    uploadTask
        .addOnFailureListener {
          warn("Failed uploading photo for ${contact.displayName} (${contact.phoneNumber})")
        }
        .addOnSuccessListener {
          OnSuccessListener<UploadTask.TaskSnapshot> {
            // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.
            info("Uploaded ${contact.displayName} (${contact.phoneNumber}) successfully")
            sharedPrefs.edit().putLong(contact.phoneNumber.toString(), now).apply()
          }
        }
  }

  // TODO: Encrypt
  fun storeMmsContent(mmsData: ByteArray, contentType: String): String? {
    if (mmsData.isEmpty()) {
      warn("Attempting to store empty mmsData")
      return null
    }

    val user = FirebaseAuth.getInstance().currentUser
    if (user == null) {
      warn("Null Firebase user, cannot auth this mmsData upload")
      return null
    }

    val attachmentId = UUID.randomUUID()
    val attachmentUuidString = attachmentId.toString()

    val storageReference = FirebaseStorage.getInstance()
        .getReference(user.uid)
        .child(imageKey)
    val attachmentReference = storageReference.child(attachmentUuidString)
    val metadata = StorageMetadata.Builder().setContentType(contentType).build()
    val uploadTask = attachmentReference.putBytes(mmsData, metadata)
    uploadTask
        .addOnFailureListener {
          warn("Failed uploading mmsData")
        }
        .addOnSuccessListener {
          OnSuccessListener<UploadTask.TaskSnapshot> {
            // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.
            debug("Uploaded mmsData successfully")
          }
        }

    return attachmentUuidString
  }

  /**
   * POST a message asynchronously.
   */
  fun postMessageAsync(
      message: Message,
      callback: (request: Request, response: Response, result: Result<String, FuelError>) -> Unit) {
    val json = message.toJSON()
    if (json == null) {
      warn("Attempting to submit null message: ${message.toList()}")
      return
    }

    Fuel
        .post(postV1MessageEndpoint)
        .body(json)
        .header(mapOf(Pair("Content-type", "application/json")))
        .responseString { request, response, result ->
          callback(request, response, result)
        }
  }

  /**
   * POST an Firebase Cloud Messaging token asynchronously.
   */
  fun postPushyTokenAsync(
      token: String,
      uid: String?,
      callback: (request: Request, response: Response, result: Result<String, FuelError>) -> Unit) {
    if (uid == null) {
      warn("Attempting to submit null token with null uid")
      return
    }

    val body = FCMToken(token, uid).toJSON()
    if (body == null) {
      warn("Attempting to submit null token")
      return
    }

    Fuel
        .post(postV1FcmTokenEndpoint)
        .body(body)
        .header(mapOf(Pair("Content-type", "application/json")))
        .responseString { request, response, result ->
          callback(request, response, result)
        }
  }

  @Serializable
  data class FCMToken(
          val token: String,
          val uid: String
  ) {
    fun toJSON(): String? {
      return JSON.stringify(this)
    }
  }

  /**
   * Parses a string phone number, setting defaults in failure cases (0).
   */
  fun parsePhoneNumber(phoneNumberString: String): Long? {
    return try {
      val parsedPhoneNumber = PhoneNumberUtil
              .getInstance()
              .parse(phoneNumberString, Locale.getDefault().country)
      parsedPhoneNumber.nationalNumber
    } catch (ignored: NumberParseException) {
      warn("Could not get this device's number from: $phoneNumberString, using raw value")
      phoneNumberString.toLongOrNull()
    }
  }

  fun parseContact(context: Context, phoneNumber: Long): ContactObject? {
    val uri = ContactsContract.PhoneLookup.CONTENT_FILTER_URI
        .buildUpon()
        .appendPath(phoneNumber.toString())
        .build()

    val contactCursor = context.contentResolver.query(
        uri,
        ContactObject.projection,
        null,
        null,
        null)!!

    val contact: ContactObject
    if (contactCursor.moveToFirst()) {
      contact = contactObject(contactCursor, phoneNumber)
    } else {
      info("parseMms -- Found no Contact for $phoneNumber")
      return null
    }

    // Retrieve the actual photo from the contact's photoUri
    if (!contact.photoUri.isNullOrEmpty()) {
      val inputStream = ContactsContract.Contacts.openContactPhotoInputStream(
          context.contentResolver,
          ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id))
      val photo: ByteArray
      val base64Photo: String
      try {
//        photo = utils.compress(IOUtils.toByteArray(inputStream), Deflater.BEST_COMPRESSION);
        photo = inputStream.readBytes()

        // TODO: Encrypt this string
        base64Photo = Base64.encodeToString(photo, Base64.DEFAULT)
        contact.photo = base64Photo
      } catch (e: Exception) {
        warn("Error getting contact photo", e)
      }
    }

    return contact
  }

  /**
   * Given an mms id, return a full MmsObject, including image and address data.
   */
  fun parseMms(context: Context, id: Long): MmsObject? {
    // Get basic MMS info
    val mmsCursor = context.contentResolver.query(
        Mms.CONTENT_URI,
        MmsObject.projection,
        "${Mms._ID} = $id",
        null,
        null)!!

    val mms: MmsObject
    if (mmsCursor.moveToFirst()) {
      mms = MmsObject(mmsCursor)
    } else {
      info("parseMms -- Found no Mms for $id")
      return null
    }

    // Get Parts
    val partCursor = context.contentResolver.query(
        MmsPart.contentUri,
        MmsPart.projection,
        "mid = $id",
        null,
        "_id DESC")!!

    while (partCursor.moveToNext()) {
      val mmsPart = MmsPart(partCursor)

      // TODO: Handle more content types
      when (mmsPart.contentType.toLowerCase()) {

        "text/plain" -> {
          mms.body = mmsPart.text
        }

        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/bmp" -> {
          mms.contentType = mmsPart.contentType
          mms.attachment = readMMSPart(context, mmsPart.id)
        }

        "video/mpeg",
        "video/mp4",
        "video/quicktime",
        "video/webm",
        "video/3gpp",
        "video/3gpp2",
        "video/3gpp-tt",
        "video/H261",
        "video/H263",
        "video/H263-1998",
        "video/H263-2000",
        "video/H264" -> {
          mms.contentType = mmsPart.contentType
          mms.attachment = readMMSPart(context, mmsPart.id)
          //          byte[] compressedImgData = byteUtils.compress(imgData, Deflater.BEST_COMPRESSION);
          // TODO: 3gpp doesn't work in the browser, convert codecs here (if possible)
          //          try {
          //            MediaCodec codec = MediaCodec.createDecoderByType("video/3gpp");
          //            codec.configure(MediaFormat.createVideoFormat("video/3gpp", 640, 480), null, null, 0);
          //            codec.queueInputBuffer();
          //            codec.start();
          //          } catch (IOException e) {
          //            e.printStackTrace();
          //          }
          //          byte[] compressedImgData = byteUtils.compress(imgData, Deflater.BEST_COMPRESSION);
        }
      }
    }
    partCursor.close()

    // TODO(nick): Encrypt the body & attachment
    mms.addresses = readMMSAddresses(context, id)
    info("MmsAddr -- addresses: ${mms.addresses}")
    return mms
  }

  private fun readMMSPart(context: Context, partId: Long): ByteArray {
    val partURI = MmsPart.contentUri.buildUpon().appendPath(partId.toString()).build()
    val inputStream = context.contentResolver.openInputStream(partURI)
    val partData = inputStream.readBytes()
    inputStream.close()

    return partData
  }

  /**
   * Takes an MMS id, retrieves the addresses (read: phone number longs) of the
   * recipients.
   */
  private fun readMMSAddresses(context: Context, id: Long): List<MmsAddr> {
    val addrUri = Mms.CONTENT_URI.buildUpon().appendEncodedPath("$id/addr").build()
    val addrCursor = context.contentResolver.query(
        addrUri,
        MmsAddr.projection,
        null,
        null,
        null)!!
    val addresses = mutableListOf<MmsAddr>()
    while (addrCursor.moveToNext()) {
      val mmsAddr = MmsAddr(addrCursor)
      val address = Util.parsePhoneNumber(mmsAddr.address)
      if (address == null) {
        warn("Retrieved an invalid address: ${mmsAddr.address}")
        continue
      }

      info("mms addr: ${mmsAddr.address}")
      info("mms addr type: ${mmsAddr.type}")
      mmsAddr.addressLong = address

      addresses.add(mmsAddr)
    }

    addrCursor.close()
    return addresses
  }

  /* Checks if external storage is available for read and write */
  private fun isExternalStorageWritable(): Boolean {
    val state = Environment.getExternalStorageState()
    return Environment.MEDIA_MOUNTED == state
  }

  /* Checks if external storage is available to at least read */
  private fun isExternalStorageReadable(): Boolean {
    val state = Environment.getExternalStorageState()
    return Environment.MEDIA_MOUNTED == state || Environment.MEDIA_MOUNTED_READ_ONLY == state
  }

  @RequiresApi(Build.VERSION_CODES.O)
  fun writeLog(log: String) {
    when {
      isExternalStorageWritable() -> {

        val logDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Hermes")

        if (!logDir.exists()) {
          val success = logDir.mkdirs()
          info("Made log dir: $success")
        }

        val writer = FileWriter(File(logDir, "log.txt"), true)
        val timestamp = LocalDateTime.now()
        writer.append("$timestamp $log\n\n")
        writer.flush()
        writer.close()
        info(log)
      }

      isExternalStorageReadable() -> {
        info("External storage is only readable")
      }

      else -> {
        info("External storage is not accessible")
      }
    }
  }
}
