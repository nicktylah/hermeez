package com.nicktylah.hermes;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.provider.Telephony.Sms;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.nicktylah.hermes.models.Message;
import com.nicktylah.hermes.util.ByteUtils;
import com.nicktylah.hermes.util.Contact;

import java.util.HashMap;
import java.util.Map;

/**
 * Listens for outgoing SMS messages at content://sms, syncs them to Firebase
 */
public class SmsReceiver extends ContentObserver {

  private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
  private final Contact contactUtil = new Contact();
  private final String TAG = "SmsReceiver";

  private FirebaseDatabaseWrapper databaseWrapper;
  private Context mContext;
  private Long ownPhoneNumber;

  public SmsReceiver(
      Handler handler,
      Context mContext) {
    super(handler);
    this.mContext = mContext;
    this.databaseWrapper = FirebaseDatabaseWrapper.getInstance();

    try {
      String line1 = ((TelephonyManager) mContext
          .getSystemService(Context.TELEPHONY_SERVICE))
          .getLine1Number();
      ownPhoneNumber = phoneNumberUtil.parse(line1, "US").getNationalNumber();
      databaseWrapper.ownPhoneNumberDBRef.setValue(ownPhoneNumber);
    } catch (NumberParseException e) {
      Log.w(TAG, "Could not determine this phone's own number");
      ownPhoneNumber = -1L;
    } catch (SecurityException e) {
      Log.w(TAG, "Could not determine this phone's own number. Permissions required");
      ownPhoneNumber = -1L;
    }
  }

  @Override
  public void onChange(boolean selfChange) {
    super.onChange(selfChange);

    Cursor cur =  mContext.getContentResolver().query(
        Sms.CONTENT_URI,
        new String[]{
            Sms._ID,
            Sms.DATE,
            Sms.ADDRESS,
            Sms.BODY,
            Sms.TYPE,
            Sms.THREAD_ID
        },
        null,
        null,
        Sms.DATE + " DESC");

    if (cur == null) {
      Log.d(TAG, "Could not query for sent sms");
      return;
    }
    if (cur.moveToNext()) {
      final Long messageId = Long.valueOf(cur.getString(cur.getColumnIndex(Sms._ID)));
      final Integer conversationId = Integer.parseInt(cur.getString(cur.getColumnIndex(Sms.THREAD_ID)));
      final Integer type = Integer.parseInt(cur.getString(cur.getColumnIndex(Sms.TYPE)));
      final Long date = Long.valueOf(cur.getString(cur.getColumnIndex(Sms.DATE)));
      String recipient = cur.getString(cur.getColumnIndex(Sms.ADDRESS));

      Map<String, Object> parsedPhoneNumber = contactUtil.parsePhoneNumber(recipient);
      final Long recipientPhoneNumber = (Long) parsedPhoneNumber.get("phoneNumber");
      if (recipientPhoneNumber == 0L) {
        Log.d(TAG, "Skipping " + conversationId + "'s message that came from \"0\"");
        return;
      }

      final String body = cur.getString(cur.getColumnIndex(Sms.BODY));

      Log.d(TAG, "New SMS message found:\n" +
          "ID: " + messageId +
          " date: " + date +
          " address: " + recipient +
          " conversationID: " + conversationId
//          " body: " + body
      );

      final ByteUtils utils = new ByteUtils();
      final String encryptedBody = utils.encrypt(mContext, body);
      final Integer countryCode = (Integer) parsedPhoneNumber.get("countryCode");

      if (databaseWrapper == null) {
        FirebaseDatabaseWrapper.init();
        databaseWrapper = FirebaseDatabaseWrapper.getInstance();
      }

      // Write this new message to Firebase
      // Wrap this in a new Thread so we can talk to Firebase in the background
      new Thread(new Runnable() {
        public void run() {
          Message message = new Message(
              messageId,
              recipientPhoneNumber,
              encryptedBody,
              "",
              "",
              date);

          if (type == Sms.MESSAGE_TYPE_SENT) {
            message.setSender(ownPhoneNumber);
          }

          databaseWrapper.messagesDBRef.child(conversationId + "/" + messageId).setValue(message);

          // Append this conversation to this recipient, creating one if none exists
          Map<String, Object> recipientUpdate = new HashMap<>();
          recipientUpdate.put("countryCode", countryCode);
          recipientUpdate.put("phoneNumber", recipientPhoneNumber);
          recipientUpdate.put("conversations/" + conversationId, true);

          databaseWrapper.recipientsDBRef
              .child(recipientPhoneNumber.toString())
              .updateChildren(recipientUpdate);

          // Append this recipient to this conversation, creating one if none exists
          Map<String, Object> conversationUpdates = new HashMap<>();
          conversationUpdates.put("id", conversationId);
          conversationUpdates.put("recipients/" + recipientPhoneNumber, true);
          conversationUpdates.put("recipients/" + ownPhoneNumber, true);

          conversationUpdates.put("lastMessageContent", encryptedBody);
          if (type == Sms.MESSAGE_TYPE_SENT) {
            conversationUpdates.put("lastMessageSender", ownPhoneNumber);
          } else {
            conversationUpdates.put("lastMessageSender", recipientPhoneNumber);
          }
          conversationUpdates.put("lastMessageAttachment", "");
          conversationUpdates.put("lastMessageAttachmentContentType", "");
          conversationUpdates.put("lastMessageTimestamp", date);

          databaseWrapper.conversationsDBRef
              .child(conversationId.toString())
              .updateChildren(conversationUpdates);
        }
      }).start();
    }
    cur.close();
  }

}
