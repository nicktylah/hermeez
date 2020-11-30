package com.nicktylah.hermes;

import android.*;
import android.Manifest;
import android.app.Activity;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.nicktylah.hermes.models.Conversation;
import com.nicktylah.hermes.models.Message;
import com.nicktylah.hermes.models.Recipient;
import com.nicktylah.hermes.util.ByteUtils;
import com.nicktylah.hermes.util.Contact;
import com.nicktylah.hermes.util.MmsHelper;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads the SMSs present on this device, and syncs those conversations/recipients with Firebase.
 */
public class Sync extends IntentService {

  public static final String STOP_SYNC_ALL = "STOP_SYNC_ALL";
  public static volatile boolean syncingAll = false;

  private FirebaseDatabaseWrapper databaseWrapper;
  volatile boolean shouldStop = false;

  private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
  private final Contact contactUtil = Contact.getInstance();
  private final String TAG = "Sync";
  private final int syncNotificationId = 1337;

  private Long ownPhoneNumber;
  private NotificationManager notificationManager;
  private Notification.Builder builder;
  private int numConversationsToSync = 0;
  private Long ONE_WEEK_MILLIS = 604800000L;
  private StorageReference storageReference;

  public Sync() {
    super("Sync");
    try {
      storageReference = FirebaseStorage.getInstance()
              .getReference("user")
              .child(FirebaseAuth.getInstance().getCurrentUser().getUid())
              .child("mms-images");
    } catch (NullPointerException e) {
      Log.d(TAG, "Could not get the current firebase user");
    }
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    if (intent == null || intent.getAction() == null) {
      Log.d(TAG, "Received null intent/intent with no action");
      return;
    }
    if (ownPhoneNumber == null) {
      if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) !=
              PackageManager.PERMISSION_GRANTED) {
        Log.d(TAG, "Permission(s) not granted");
        return;
      }
      String line1 = ((TelephonyManager) this.getApplicationContext()
              .getSystemService(Context.TELEPHONY_SERVICE))
              .getLine1Number();
      try {
        ownPhoneNumber = phoneNumberUtil.parse(line1, "US").getNationalNumber();
      } catch (NumberParseException e) {
        Log.w(TAG, "Could not determine this phone's own number");
        ownPhoneNumber = -1L;
      }
    }
    if (databaseWrapper == null) {
      databaseWrapper = FirebaseDatabaseWrapper.getInstance();
      assert databaseWrapper != null;
    }

    Intent notificationIntent = new Intent(this, Sync.class);
    notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    PendingIntent pendingIntent = PendingIntent.getService(
        this.getApplicationContext(),
        (int) System.currentTimeMillis(),
        notificationIntent,
        PendingIntent.FLAG_UPDATE_CURRENT);
    builder = new Notification.Builder(this)
        .setContentTitle(getText(R.string.sync_notification_title))
        .setSmallIcon(R.drawable.hermes_white)
        .setContentIntent(pendingIntent);

    switch (intent.getAction()) {
      case (MainActivity.SYNC_MESSAGES):
        Log.d(TAG, "sync messages");
        sync();
        break;
      case (STOP_SYNC_ALL):
        Log.d(TAG, "stop sync all messages");
        syncingAll = false;
        stopSelf();
        break;
    }

  }

  private boolean isWifiConnected() {
    final WifiManager wifiManager = (WifiManager) getApplicationContext()
        .getSystemService(Context.WIFI_SERVICE);
    assert wifiManager != null;
    if (wifiManager.isWifiEnabled()) {
      WifiInfo wifiInfo = wifiManager.getConnectionInfo();
      return !(wifiInfo == null || wifiInfo.getNetworkId() == -1);
    } else {
      return false;
    }
  }

  private class SyncMessages extends AsyncTask<Integer, Void, Integer> {

    // Sync up to 100 SMS messages and up to 50 MMS messages (additive)
    private int numSmsMessagesToSync = 100;
    private int numMmsMessagesToSync = 50;
    private int conversationId;
    private Long waitTimeMs = 250L;
    private Long previousTimestamp;
    private CountDownLatch cdl;
    private Set<Map<String, Object>> recipients;
    private AtomicInteger numMessagesSynced;

    public SyncMessages(
            CountDownLatch cdl,
            Long previousTimestamp,
            Set<Map<String, Object>> recipients,
            AtomicInteger numMessagesSynced) {
      this.cdl = cdl;
      this.previousTimestamp = previousTimestamp;
      this.recipients = recipients;
      this.numMessagesSynced = numMessagesSynced;
    }

    @Override
    protected Integer doInBackground(Integer... params) {
      conversationId = params[0];
      Log.d(TAG, "Starting background sync for conversationID: " + conversationId);
      final MmsHelper mmsHelper = new MmsHelper(getApplicationContext());
      final ByteUtils byteUtils = new ByteUtils();
      final Contact contactUtil = Contact.getInstance();

      final Cursor smsCursor;
      smsCursor = getApplicationContext().getContentResolver().query(
          Sms.CONTENT_URI,
          new String[]{ // SELECT
              Sms._ID,
              Sms.ADDRESS,
              Sms.BODY,
              Sms.DATE,
              Sms.PERSON,
              Sms.TYPE,
              Sms.THREAD_ID
          },
          Sms.THREAD_ID + " = " + conversationId,
          null,
          Sms.DATE + " DESC" // ORDER BY
      );
      if (smsCursor == null) {

        return 0;
      }

      final Map<Integer, Long> lastConversationTimestamp = new HashMap<>();

      // Sync SMS
      while (smsCursor.moveToNext()) {
        if (smsCursor.getPosition() >= numSmsMessagesToSync || shouldStop) {
          break;
        }

        final Integer conversationId = Integer.parseInt(
            smsCursor.getString(smsCursor.getColumnIndex(Sms.THREAD_ID)));
        final Long messageId = Long.valueOf(
            smsCursor.getString(smsCursor.getColumnIndex(Sms._ID)));
        String body = byteUtils.encrypt(
            getApplicationContext(),
            smsCursor.getString(smsCursor.getColumnIndex(Sms.BODY)));
        final Long date = Long.valueOf(
            smsCursor.getString(smsCursor.getColumnIndex(Sms.DATE)));
        final Integer type = Integer.parseInt(
            smsCursor.getString(smsCursor.getColumnIndex(Sms.TYPE)));
        final String phoneNumberString =
            smsCursor.getString(smsCursor.getColumnIndex(Sms.ADDRESS));

        // If we've already synced to this point (based on timestamp), bail
        if (date <= previousTimestamp) {
          break;
        }

        final String s = conversationId + "/" + messageId;
        if (databaseWrapper == null) {
          Log.d(TAG, "Attempting to use a null messagesDBRef");
          return 0;
        }
        final String finalBody = body;
        databaseWrapper.messagesDBRef
            .child(s)
            .addListenerForSingleValueEvent(new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot dataSnapshot) {
                Map<String, Object> parsedPhoneNumber = contactUtil.parsePhoneNumber(phoneNumberString);
                Long phoneNumber = (Long) parsedPhoneNumber.get("phoneNumber");
                Integer countryCode = (Integer) parsedPhoneNumber.get("countryCode");
                if (phoneNumber == 0L) {
                  Log.d(TAG, "Skipping " + conversationId + "'s message that came from \"0\"");
                  return;
                }
                recipients.add(parsedPhoneNumber);

                // Write this new message to Firebase
                Message message = new Message(
                    messageId,
                    phoneNumber,
                    finalBody,
                    "",
                    "",
                    date);

                if (type == Sms.MESSAGE_TYPE_SENT) {
                  message.setSender(ownPhoneNumber);
                }
                databaseWrapper.messagesDBRef.child(s).setValue(message);

                // Append this conversation to this recipient, creating one if none exists
                Map<String, Object> recipientUpdate = new HashMap<>();
                recipientUpdate.put("countryCode", countryCode);
                recipientUpdate.put("phoneNumber", phoneNumber);
                recipientUpdate.put("conversations/" + conversationId, true);

                databaseWrapper.recipientsDBRef.child(phoneNumber.toString()).updateChildren(recipientUpdate);

                // Append this recipient to this conversation, creating one if none exists
                Map<String, Object> conversationUpdates = new HashMap<>();
                conversationUpdates.put("id", conversationId);
                conversationUpdates.put("recipients/" + phoneNumber, true);
                conversationUpdates.put("recipients/" + ownPhoneNumber, true);

                // Determine if we need to update the "lastMessage" information in this conversation
                if (!dataSnapshot.exists() &&
                    (!lastConversationTimestamp.containsKey(conversationId) ||
                        lastConversationTimestamp.get(conversationId) < date)) {
                  conversationUpdates.put("lastMessageContent", finalBody);
                  if (type == Sms.MESSAGE_TYPE_SENT) {
                    conversationUpdates.put("lastMessageSender", ownPhoneNumber);
                  } else {
                    conversationUpdates.put("lastMessageSender", phoneNumber);
                  }
                  conversationUpdates.put("lastMessageTimestamp", date);
                  conversationUpdates.put("lastMessageAttachment", "");
                  conversationUpdates.put("lastMessageAttachmentContentType", "");
                  lastConversationTimestamp.put(conversationId, date);
                }

                databaseWrapper.conversationsDBRef
                    .child(conversationId.toString())
                    .updateChildren(conversationUpdates);
              }

              @Override
              public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "Firebase database error", databaseError.toException());
                shouldStop = true;
              }
            });

        // Sleep so we don't go crazy registering a zillion callbacks before we have a chance to set
        // the shouldStop boolean
        try {
          Thread.sleep(waitTimeMs);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      int numSmsSynced = smsCursor.getPosition() + 1;
      smsCursor.close();

      // Sync MMS Messages
      final Cursor mmsCursor;
      try {
        mmsCursor = getApplicationContext().getContentResolver().query(
            Mms.CONTENT_URI,
            new String[]{
                Mms.DATE,
                Mms._ID,
                Mms.THREAD_ID
            },
            Mms.THREAD_ID + " = " + conversationId, // WHERE
            null,
            Mms.DATE + " DESC" // ORDER BY
        );
        assert mmsCursor != null;
      } catch (NullPointerException e) {
        Log.e(TAG, "Could not get mms messages", e);
        return 0;
      }

      while (mmsCursor.moveToNext()) {
        if (mmsCursor.getPosition() >= numMmsMessagesToSync || shouldStop) {
          break;
        }

//      Log.d(TAG, "content://mms columns");
//      String[] cols = mmsCursor.getColumnNames();
//      for (String col : cols) {
//        Log.d(TAG, col + ": " + mmsCursor.getString(mmsCursor.getColumnIndex(col)));
//      }

        // MMS timestamps are in seconds
        final Long date = Long.valueOf(
            mmsCursor.getString(mmsCursor.getColumnIndex(Mms.DATE))) * 1000;
        final Long messageId = Long.valueOf(
            mmsCursor.getString(mmsCursor.getColumnIndex(Mms._ID)));
        final Integer conversationId = Integer.parseInt(
            mmsCursor.getString(mmsCursor.getColumnIndex(Mms.THREAD_ID)));

        // If we've already synced to this point (based on timestamp), bail
        if (date <= previousTimestamp) {
          Log.d(TAG, "Already synced up to the message at timestamp: " + date);
          break;
        }

        final Map<String, Object> mms = mmsHelper.parseMms(messageId);

        final String s = conversationId + "/" + messageId;
        databaseWrapper.messagesDBRef
            .child(s)
            .addListenerForSingleValueEvent(new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot dataSnapshot) {
                Map<String, Object> parsedSenderPhoneNumber =
                    contactUtil.parsePhoneNumber((String) mms.get("sender"));
                String body = (String) mms.get("body");
                String attachment = (String) mms.get("attachment");
                String attachmentContentType = (String) mms.get("attachmentContentType");
                Long senderPhoneNumber = (Long) parsedSenderPhoneNumber.get("phoneNumber");
                if (senderPhoneNumber == 0L) {
                  Log.d(TAG, "Skipping " + conversationId + "'s message that came from \"0\"");
                  return;
                }
                recipients.add(parsedSenderPhoneNumber);

                final String attachmentUuidString;
                if (!Objects.equals(attachment, "")) {
                  UUID attachmentId = UUID.randomUUID();
                  attachmentUuidString = attachmentId.toString();

                  StorageReference attachmentReference = storageReference
                      .child(attachmentUuidString);
                  UploadTask uploadTask = null;
                  try {
                    uploadTask = attachmentReference.putBytes(attachment.getBytes("UTF-8"));
                  } catch (UnsupportedEncodingException e) {
                    Log.w(TAG, "Error retrieving attachment bytes");
                  }
                  uploadTask.addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception exception) {
                      Log.d(TAG, "Failed uploading photo for " + attachmentUuidString);
                    }
                  }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                      // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.
                      Log.d(TAG, "Uploaded " + attachmentUuidString);
                    }
                  });
                } else {
                  attachmentUuidString = "";
                }

                // Write this new message to Firebase
                databaseWrapper.messagesDBRef.child(s).setValue(
                    new Message(
                        messageId,
                        senderPhoneNumber,
                        body,
                        attachmentUuidString,
                        attachmentContentType,
                        date)
                );

                // Write these recipients to Firebase
                Set<Long> recipientIdLongs = new HashSet<>();
                for (String phoneNumberString : (Set<String>) mms.get("recipientIds")) {
                  Map<String, Object> parsedPhoneNumber = contactUtil.parsePhoneNumber(phoneNumberString);
                  Long phoneNumber = (Long) parsedPhoneNumber.get("phoneNumber");
                  Integer countryCode = (Integer) parsedPhoneNumber.get("countryCode");
                  if (phoneNumber == 0L) {
                    Log.d(TAG, "Skipping " + conversationId + "'s message that came from \"0\"");
                    return;
                  }
                  recipients.add(parsedPhoneNumber);

                  recipientIdLongs.add(phoneNumber);

                  // Append this conversation to this recipient, creating one if none exists
                  Map<String, Object> recipientUpdate = new HashMap<>();
                  recipientUpdate.put("countryCode", countryCode);
                  recipientUpdate.put("phoneNumber", phoneNumber);
                  recipientUpdate.put("conversations/" + conversationId, true);

                  databaseWrapper.recipientsDBRef.child(phoneNumber.toString()).updateChildren(recipientUpdate);
                }


                // Append this recipient to this conversation, creating one if none exists
                Map<String, Object> conversationUpdates = new HashMap<>();
                conversationUpdates.put("id", conversationId);
                for (Long phoneNumber : recipientIdLongs) {
                  if (phoneNumber == 0L) {
                    Log.d(TAG, "Skipping " + conversationId + "'s message that came from \"0\"");
                    continue;
                  }
                  conversationUpdates.put("recipients/" + phoneNumber, true);
                }

                // Determine if we need to update the "lastMessage" information in this conversation
                if (!dataSnapshot.exists() &&
                    (!lastConversationTimestamp.containsKey(conversationId) ||
                        lastConversationTimestamp.get(conversationId) < date)) {
                  conversationUpdates.put("lastMessageContent", body);
                  conversationUpdates.put("lastMessageTimestamp", date);
                  conversationUpdates.put("lastMessageSender", senderPhoneNumber);
                  conversationUpdates.put("lastMessageAttachment", attachmentUuidString);
                  conversationUpdates.put("lastMessageAttachmentContentType", mms.get("attachmentContentType"));
                  lastConversationTimestamp.put(conversationId, date);
                }

                databaseWrapper.conversationsDBRef
                    .child(conversationId.toString())
                    .updateChildren(conversationUpdates);
              }

              @Override
              public void onCancelled(DatabaseError databaseError) {
                Log.w(TAG, "Firebase database error", databaseError.toException());
                shouldStop = true;
              }
            });

        // Sleep so we don't go crazy registering a zillion callbacks before we have a chance to set
        // the shouldStop boolean
        try {
          Thread.sleep(waitTimeMs);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      int numMmsSynced = mmsCursor.getPosition() + 1;
      mmsCursor.close();
      numMessagesSynced.getAndAdd(numSmsSynced + numMmsSynced);
      return numSmsSynced + numMmsSynced;
    }

    @Override
    protected void onPostExecute(Integer result) {
      cdl.countDown();
      Log.d(TAG, "Finished background sync for conversationID: " + conversationId +
          ". Synced " + result + " messages");

      if (cdl.getCount() <= 0) {
        return;
      }

      try {
        int progress = numConversationsToSync - (int) cdl.getCount();
        builder = builder.setProgress(numConversationsToSync, progress, false);
        notificationManager.notify(syncNotificationId, builder.build());
      } catch (Exception e) {
        Log.w(TAG, "Error updating progress bar", e);
      }
    }
  }

  /**
   * Called periodically, syncs backwards chronologically until it finds a message that already
   * exists in Firebase, at which point it stops.
   */
  private void sync() {
    if (databaseWrapper == null) {
      databaseWrapper = FirebaseDatabaseWrapper.getInstance();
      if (databaseWrapper == null) {
        FirebaseDatabaseWrapper.init();
        databaseWrapper = FirebaseDatabaseWrapper.getInstance();
      }
    }

    final Context mContext = getApplicationContext();

    if (!isWifiConnected()) {
      Log.d(TAG, "Sync will not take place if wifi is off");
      int wifiNotificationId = 1337;
      NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      Notification.Builder syncSmsNotificationBuilder = new Notification.Builder(this)
          .setContentTitle("Automatic sync prevented")
          .setContentText("Sync will not take place if wifi is off")
          .setSmallIcon(R.drawable.hermes_white);
      notificationManager.notify(wifiNotificationId, syncSmsNotificationBuilder.build());

      try {
        Thread.sleep(1000L);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      notificationManager.cancel(wifiNotificationId);
      return;
    }

    if (ownPhoneNumber != null) {
      databaseWrapper.ownPhoneNumberDBRef.setValue(ownPhoneNumber);
    }

    if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "No READ_SMS permission granted");
      return;
    }

    // Get the 100 most recent conversationIDs
    final int maxConversationsToSync = 50;
    final int maxMessagesToScan = 1000;
    final Cursor smsConversationsCursor;
      smsConversationsCursor = mContext.getContentResolver().query(
          Sms.CONTENT_URI,
          new String[]{Sms.THREAD_ID},
          null,
          null,
          Sms._ID + " DESC"
      );
    if (smsConversationsCursor == null) {
      Log.d(TAG, "Error querying conversations (SMS)");
      return;
    }
    final Cursor mmsConversationsCursor;
    mmsConversationsCursor = mContext.getContentResolver().query(
        Mms.CONTENT_URI,
        new String[]{Mms.THREAD_ID},
//        Mms.THREAD_ID + " = 805",
        null,
        null,
        Mms._ID + " DESC"
    );
    if (mmsConversationsCursor == null) {
      Log.d(TAG, "Error querying conversations (MMS)");
      return;
    }

    Set<Integer> conversationsToSync = new HashSet<>();
    // For each conversationID, kick off async jobs to fetch up to 200 recent messages
    while(smsConversationsCursor.moveToNext()) {
      if (smsConversationsCursor.getPosition() >= maxMessagesToScan) {
        break;
      }

      final Integer conversationId = Integer.parseInt(
          smsConversationsCursor.getString(smsConversationsCursor.getColumnIndex(Sms.THREAD_ID)));
      conversationsToSync.add(conversationId);

      if (conversationsToSync.size() >= maxConversationsToSync) {
        break;
      }
    }

    while(mmsConversationsCursor.moveToNext()) {
      if (mmsConversationsCursor.getPosition() >= maxMessagesToScan) {
        break;
      }

      final Integer conversationId = Integer.parseInt(
          mmsConversationsCursor.getString(mmsConversationsCursor.getColumnIndex(Mms.THREAD_ID)));
      conversationsToSync.add(conversationId);

      if (conversationsToSync.size() >= maxConversationsToSync) {
        break;
      }
    }

    numConversationsToSync = conversationsToSync.size();

    smsConversationsCursor.close();
    mmsConversationsCursor.close();

    final CountDownLatch cdl = new CountDownLatch(numConversationsToSync);
    HashSet<Map<String, Object>> recipients = new HashSet<>();
    final Set<Map<String, Object>> synchronizedRecipients = java.util.Collections.synchronizedSet(recipients);
    final AtomicInteger numMessagesSynced = new AtomicInteger(0);

    for (final Integer conversationId : conversationsToSync) {
      databaseWrapper.conversationsDBRef
          .child(conversationId.toString())
          .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
              Long previousTimestamp = 0L;
              if (dataSnapshot.exists()) {
                Conversation conversation = dataSnapshot.getValue(Conversation.class);
                previousTimestamp = conversation.getLastMessageTimestamp();
              }
              new SyncMessages(cdl, previousTimestamp, synchronizedRecipients, numMessagesSynced)
                  .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, conversationId);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
              Log.d(TAG, "Failed to query Firebase for this conversation. Aborting");
              cdl.countDown();
            }
          });
    }

    builder = builder.setProgress(numConversationsToSync, 0, false);
    notificationManager.notify(syncNotificationId, builder.build());

    try {
      Log.d(TAG, "Starting sync for " + numConversationsToSync + " conversations");
      Long startTime = System.currentTimeMillis();
      cdl.await();
      Log.d(TAG, "Synced " + numConversationsToSync + " conversations in: " +
          (System.currentTimeMillis() - startTime) + " ms. " +
          numMessagesSynced.get() + " messages synced.");
      Log.d(TAG, String.valueOf(synchronizedRecipients));
      Log.d(TAG, "Unique recipients: " + synchronizedRecipients.size());

      syncRecipients(synchronizedRecipients);

      // Hackery to make the notification finish every time
      Thread.sleep(100);
      builder = builder
          .setContentText("Synced " + numMessagesSynced.get() + " messages in " +
              numConversationsToSync + " conversations.")
          .setProgress(0, 0, false);
      notificationManager.notify(syncNotificationId, builder.build());
    } catch (InterruptedException e) {
      Log.w(TAG, "Error during sync", e);
      builder = builder
          .setContentText("Error during sync")
          .setProgress(0, 0, false);
      notificationManager.notify(syncNotificationId, builder.build());
    }

    try {
      Thread.sleep(1000L);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    notificationManager.cancel(syncNotificationId);
  }

  private void syncRecipients(Set<Map<String, Object>> recipients) {
    for (final Map<String, Object> parsedPhoneNumber : recipients) {
      final Long phoneNumber = (Long) parsedPhoneNumber.get("phoneNumber");
      final Integer countryCode = (Integer) parsedPhoneNumber.get("countryCode");
      databaseWrapper.recipientsDBRef
          .child(phoneNumber.toString())
          .addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
              Log.d(TAG, "phone: " + phoneNumber);
              Long updateTime = System.currentTimeMillis();
              ArrayList<String> attributesToQuery = new ArrayList<>();
              attributesToQuery.add(PhoneLookup._ID);
              attributesToQuery.add(PhoneLookup.DISPLAY_NAME);
              attributesToQuery.add(PhoneLookup.PHOTO_URI);

              // If we've updated recently, skip this phone number
              if (dataSnapshot.exists()) {
                Recipient recipient = dataSnapshot.getValue(Recipient.class);
                Long lastUpdated = recipient.getLastUpdated();
                if (lastUpdated == null) {
                  lastUpdated = 0L;
                }
                Log.d(TAG, "recipient photouri: " + recipient.getPhotoUri());
                if (updateTime - lastUpdated <= ONE_WEEK_MILLIS) {
                  return;
                }
              }

              Map<String, String> contactAttributes = contactUtil.queryContact(
                  getApplicationContext(),
                  phoneNumber,
                  attributesToQuery);
              String recipientId = contactAttributes.get(PhoneLookup._ID);
              String recipientName = contactAttributes.get(PhoneLookup.DISPLAY_NAME);

              // Append this conversation to this recipient, creating one if none exists
              Map<String, Object> recipientUpdate = new HashMap<>();
              if (recipientId != null) {
                recipientUpdate.put("id", Long.valueOf(recipientId));
              }
              recipientUpdate.put("name", recipientName);
              recipientUpdate.put("countryCode", countryCode);
              recipientUpdate.put("phoneNumber", phoneNumber);
              recipientUpdate.put("lastUpdated", updateTime);

              databaseWrapper.recipientsDBRef.child(phoneNumber.toString()).updateChildren(recipientUpdate);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
              Log.d(TAG, "Failed to query Firebase for this recipient. Aborting");
            }
          });
    }
  }
}
