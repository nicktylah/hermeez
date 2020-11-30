package nicktylah.hermes

import android.app.job.JobParameters
import android.app.job.JobService
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import android.provider.Telephony.TextBasedSmsColumns.*
import kotlinx.coroutines.experimental.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.warn

class MmsSmsJobService : JobService(), AnkoLogger {

  private val maxSmsesToSend = 25
  private val maxMmsesToSend = 25
  private val jobs = mutableListOf<Job>()

  // Note: Because 1) we are forced to listen to content://mms-sms
  // and 2) because we don't have an ID to query with, we make approximations
  // in order to determine the real messages here.Keep track of the last ID
  // we sent up, send all new message up to that point. Note: If many messages
  // are received/sent in a short time, we may send duplicates.
  override fun onStartJob(params: JobParameters?): Boolean {
    // Reschedule for the next content change
    Util.scheduleMmsSmsJob(applicationContext)

    if (params == null) {
      info("Called with null params")
      return false
    }

    if (params.triggeredContentAuthorities == null) {
      info("No triggered content authorities")
      jobFinished(params, true)
      return false
    }

    // Do we care about the content uris?
    if (params.triggeredContentUris == null) {
      info("No triggered content uris")
      jobFinished(params, true)
      return false
    }

    async(CommonPool) {
      var needsReschedule = false
      val deadline = System.currentTimeMillis() - MmsObject.deadlineMillis
      // TODO: figure out why we overwrite our own number
//        val ownPhoneNumber = Util.getOwnPhoneNumber(applicationContext)
      val ownPhoneNumber = 0L
      var firstSmsId = 0L
      val lastSmsId = Util.getLastSmsId(applicationContext)
      val smses = mutableListOf<SmsObject>()
      var firstMmsId = 0L
      val lastMmsId = Util.getLastMmsId(applicationContext)
      val mmses = mutableListOf<MmsObject>()

      Util.writeLog("================================================================")
      Util.writeLog("MmsSmsJobService.onStartJob : Triggered content authority: ${params.triggeredContentAuthorities.joinToString(",")}")

      // Get most recent sms
      val smsCursor = applicationContext.contentResolver.query(
          Sms.CONTENT_URI,
          SmsObject.projection,
          null,
          null,
          "${Sms._ID} DESC")!!

      if (smsCursor.moveToFirst()) {
        val firstSms = smsObject(smsCursor)
        firstSmsId = firstSms.id
        var log = "MmsSmsJobService.onStartJob : Most recent SMS: ${firstSms.body} (${firstSms.id}) (${(deadline - firstSms.date)/1000} s diff from deadline, ${(System.currentTimeMillis() - firstSms.date)/1000} s diff from now)"
        if (firstSms.date < deadline) {
          log += " AFTER DEADLINE"
        }
        if (firstSms.id > lastSmsId) {
          log += " AFTER LAST ID"
        }
        Util.writeLog(log)

        if (lastSmsId == 0L) {
          info("No stored last sms id; only sending most recent sms")
          smses.add(firstSms)
        } else {
          if (firstSms.id > lastSmsId) {
            smses.add(firstSms)
          }

          var smsesAdded = 0
          while (smsCursor.moveToNext()) {
            if (smsesAdded >= maxSmsesToSend) {
              break
            }
            val sms = smsObject(smsCursor)

            if (sms.id <= lastSmsId) {
              break
            }

            var log = "MmsSmsJobService.onStartJob : SMS: ${sms.body} (${sms.id}) (${(deadline - sms.date)/1000} s diff from deadline, ${(System.currentTimeMillis() - sms.date)/1000} s diff from now)"
            if (sms.date < deadline) {
              log += " AFTER DEADLINE"
            }
            Util.writeLog(log)

            smses.add(sms)
            smsesAdded++
          }
        }
      }

      // Get most recent mms
      // Get most recent mms id(s)
      val mmsCursor = applicationContext.contentResolver.query(
              Mms.CONTENT_URI,
              MmsObject.projection,
              null,
              null,
              "${Mms._ID} DESC")!!

      if (mmsCursor.moveToFirst()) {
        val firstBasicMms = MmsObject(mmsCursor)
        val firstMms = Util.parseMms(applicationContext, firstBasicMms.id)

        if (firstMms != null) {
          firstMmsId = firstMms.id
          var log = "MmsSmsJobService.onStartJob : Most recent MMS: ${firstMms.body} (${firstMms.id}) (${(deadline - firstMms.date)/1000} s diff from deadline, ${(System.currentTimeMillis() - firstMms.date)/1000} s diff from now)"
          if (firstMms.date < deadline) {
            log += " AFTER DEADLINE"
          }
          if (firstMms.id > lastMmsId) {
            log += " AFTER LAST ID"
          }
          Util.writeLog(log)


          if (lastMmsId == 0L) {
            info("No stored last mms id; only sending most recent mms")
          } else {
            if (firstMms.id > lastMmsId) {
              mmses.add(firstMms)
            }

            var mmsesAdded = 0
            while (mmsCursor.moveToNext()) {
              if (mmsesAdded >= maxMmsesToSend) {
                break
              }
              val basicMms = MmsObject(mmsCursor)
              if (basicMms.id <= lastMmsId) {
                break
              }

              val mms = Util.parseMms(applicationContext, basicMms.id) ?: break

              var log = "MmsSmsJobService.onStartJob : MMS: ${mms.body} (${mms.id}) (${(deadline - mms.date)/1000} s diff from deadline, ${(System.currentTimeMillis() - mms.date)/1000} s diff from now)"
              if (mms.date < deadline) {
                log += " AFTER DEADLINE"
              }
              Util.writeLog(log)

              mmses.add(mms)
              mmsesAdded++
            }
          }
        }
      }

      smsCursor.close()
      mmsCursor.close()

      Util.writeLog("${smses.size} smses to write; ${mmses.size} mmses to write")
      for (sms in smses) {
        try {
          val address = Util.parsePhoneNumber(sms.address)
          if (address == null) {
            warn("Invalid phone number. Could not parse long from ${sms.address}")
            continue
          }

          // Usually true, but we must reset to ownPhoneNumber based on type
          var sender = address
          val addresses = setOf(address)
          // Set ownPhoneNumber in sharedPreferences if we don't have it
//            if (Util.getOwnPhoneNumber(applicationContext) == 0L) {
          var type = ""
          when (sms.type) {
            MESSAGE_TYPE_ALL -> type = "ALL"
            MESSAGE_TYPE_INBOX -> type = "INBOX"
            MESSAGE_TYPE_SENT -> type = "SENT"
            MESSAGE_TYPE_DRAFT -> type = "DRAFT"
            MESSAGE_TYPE_OUTBOX -> type = "OUTBOX"
            MESSAGE_TYPE_FAILED -> type = "FAILED"
            MESSAGE_TYPE_QUEUED -> type = "QUEUED"
          }

          info("MmsSmsJobService.onStartJob -- Got message type: $type from ${sms.body} (${sms.id}) Sender: $address ownPhoneNumber: $ownPhoneNumber")
          if (sms.type == MESSAGE_TYPE_SENT) {
//            Util.setOwnPhoneNumber(applicationContext, address)
            sender = ownPhoneNumber
          }

          val contact = Util.parseContact(applicationContext, sender)
          if (contact != null) {
            info("MmsSmsJobService.onStartJob -- Parsed contact from $address: ${contact.displayName}")
            Util.storeContact(applicationContext, contact)
          }

          val message = Message(
                  addresses,
                  null,
                  sms.body,
                  null,
                  sms.date,
                  sender,
                  address, // We use the address of the recipient to identify the thread
                  sms.type
          )

          // TODO(nick): Write the sms to firebase for backwards compatibility
          val job = launch(CommonPool) {
            Util.postMessageAsync(message, { request, response, _ ->
              info("MmsSmsJobService.onStartJob -- request: ${message.toJSON()}\nresponse: ${response.statusCode}")
              info("Request url: ${request.url}")
              if (response.statusCode != 200) {
                Util.writeLog("MmsSmsJobService.onStartJob -- Non 200 Status Code received! Attempting reschedule")
                needsReschedule = true
              }
            })
          }
          jobs += job
        } catch (e: Exception) {
          Util.writeLog("MmsSmsJobService.onStartJob : Exception caught!\n${e.stackTrace}")
          throw e
        }
      }

      for (mms in mmses) {
        try {
          val sender = if (mms.addresses.size == 1) {
            mms.addresses.first()
          } else {
            mms.addresses.find { mmsAddr ->
              mmsAddr.type == MmsAddr.typeFrom
            }
          }

          var senderLong = sender?.addressLong ?: ownPhoneNumber

          // The addresses field never contains ourself, so we override it if none of the
          // found addresses have the "FROM" type
          if (mms.addresses.size == 1 && mms.addresses.first().type != MmsAddr.typeFrom) {
              senderLong = ownPhoneNumber
          }

          info("MmsSmsJobService -- ownPhoneNumber: $ownPhoneNumber")
          info("MmsSmsJobService -- addresses: ${mms.addresses}")
          info("MmsSmsJobService -- sender: $senderLong")

          val type = if (senderLong == ownPhoneNumber) {
            MESSAGE_TYPE_SENT
          } else {
            1
          }

          val threadId = if (mms.addresses.size == 1) {
            mms.addresses.first().addressLong
          } else if (mms.addresses.size == 2 &&
                  mms.addresses.map { addr -> addr.addressLong }.distinct().size == 1) {
            // Edge case: 2 addresses, but both are own phone number
            mms.addresses.first().addressLong
          } else {
            mms.threadId
          }

          val addresses = mutableSetOf<Long>()
          for (addr in mms.addresses) {
            addresses.add(addr.addressLong)
            val contact = Util.parseContact(applicationContext, addr.addressLong)
            if (contact != null) {
              info("MmsSmsJobService -- parsed contact from ${addr.address}: ${contact.displayName}")
              Util.storeContact(applicationContext, contact)
            }
          }

          var attachmentUuid: String? = null
          if (mms.attachment.isNotEmpty()) {
            attachmentUuid = Util.storeMmsContent(mms.attachment, mms.contentType)
          }

          val message = Message(
                  addresses,
                  attachmentUuid,
                  mms.body,
                  mms.contentType,
                  mms.date,
                  senderLong,
                  threadId,
                  type
          )

          // TODO(nick): Write the mms to firebase for backwards compatibility
          val job = launch(CommonPool) {
            Util.postMessageAsync(message, { _, response, result ->
              info("MmsSmsJobService -- Writing mms:\n" +
                  "id: ${mms.id}\n" +
                  "addresses: ${message.addresses}\n" +
                  "attachment: $attachmentUuid\n" +
                  "body: ${message.body}\n" +
                  "contentType: ${message.contentType}\n" +
                  "date: ${message.date}\n" +
                  "sender: ${message.sender}\n" +
                  "threadId: ${message.threadId}\n" +
                  "type: ${message.type}")
              info("MmsSmsJobService -- request: ${message.toJSON()}\nresponse: ${response.statusCode}, result: $result")
              if (response.statusCode != 200) {
                Util.writeLog("MmsSmsJobService -- Non 200 Status Code received! Attempting reschedule")
                needsReschedule = true
              }
              info("MmsSmsJobService -- Called jobFinished. NeedsReschedule: $needsReschedule")
            })
          }
          jobs += job
        } catch (e: Exception) {
          Util.writeLog("MmsSmsJobService.onStartJob : Exception caught!\n${e.stackTrace}")
          throw e
        }
      }

      jobs.forEach { j -> j.join() }
      Util.setLastSmsId(applicationContext, firstSmsId)
      Util.setLastMmsId(applicationContext, firstMmsId)
      jobFinished(params, needsReschedule)
    }

    return true // true if we have background thread(s) running
  }

  override fun onStopJob(params: JobParameters?): Boolean {
    // Do any cleanup necessary here, return true to be rescheduled (this job)
    info("onStop")
    jobs.forEach { j -> j.cancel() }
    return false
  }
}
