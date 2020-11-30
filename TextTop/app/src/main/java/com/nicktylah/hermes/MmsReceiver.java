package com.nicktylah.hermes;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.Telephony.Mms;
import android.support.annotation.NonNull;
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
import com.nicktylah.hermes.models.Message;
import com.nicktylah.hermes.util.Contact;
import com.nicktylah.hermes.util.MmsHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Listens for outgoing MMS messages at content://mms, syncs them to Firebase
 */
public class MmsReceiver extends ContentObserver {

  private final Contact contactUtil = new Contact();
  private final String TAG = "MmsReceiver";

  private FirebaseDatabaseWrapper databaseWrapper;
  private MmsHelper mmsHelper;
  private Context mContext;
  private StorageReference storageReference;

  public MmsReceiver(
      Handler handler,
      Context mContext) {
    super(handler);
    mmsHelper = new MmsHelper(mContext);
    this.mContext = mContext;
    this.databaseWrapper = FirebaseDatabaseWrapper.getInstance();
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
  public void onChange(boolean selfChange) {
    super.onChange(selfChange);

    Cursor mmsCursor = mContext.getContentResolver().query(
        Mms.CONTENT_URI,
        new String[]{
            Mms._ID,
            Mms.DATE,
            Mms.THREAD_ID
        },
        null,
        null,
        Mms.DATE + " DESC");

    if (mmsCursor == null) {
      Log.d(TAG, "Could not query for mms");
      return;
    }
    if (mmsCursor.moveToNext()) {
      final Integer conversationId = Integer.parseInt(mmsCursor.getString(mmsCursor.getColumnIndex(Mms.THREAD_ID)));
      final Long date = Long.valueOf(mmsCursor.getString(mmsCursor.getColumnIndex(Mms.DATE))) * 1000;
      final Long messageId = Long.valueOf(mmsCursor.getString(mmsCursor.getColumnIndex(Mms._ID)));

      Log.d(TAG, "New MMS message found:\n" +
          "ID: " + messageId +
          " date: " + date +
          " conversationID: " + conversationId
      );

      if (databaseWrapper == null) {
        FirebaseDatabaseWrapper.init();
        databaseWrapper = FirebaseDatabaseWrapper.getInstance();
      }

      // Write this new message to Firebase
      // Wrap this in a new Thread so we can talk to Firebase in the background
      new Thread(new Runnable() {
        public void run() {
          databaseWrapper.messagesDBRef
              .child(conversationId + "/" + messageId)
              .addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {

                  // We have to check that it doesn't already exist because we're forced to listen on the
                  // content://mms-sms url -- and we run the risk of overwriting a valid, newer SMS for
                  // this conversation if we don't
                  // TODO(nick): Learn why we can't/when we'll be able to listen properly on content://mms
                  if (dataSnapshot.exists()) {
                    Log.d(TAG, "This message already exists");
                  } else {
                    Log.d(TAG, "Writing new outgoing message to Firebase");

                    Map<String, Object> mms = mmsHelper.parseMms(messageId);
                    String sender = (String) mms.get("sender");
                    Map<String, Object> parsedSenderPhoneNumber = contactUtil.parsePhoneNumber(sender);
                    Long senderPhoneNumber = (Long) parsedSenderPhoneNumber.get("phoneNumber");
                    if (senderPhoneNumber == 0L) {
                      Log.d(TAG, "Skipping " + conversationId + "'s message that came from \"0\"");
                      return;
                    }
                    String body = (String) mms.get("body");
                    String attachment = (String) mms.get("attachment");
                    String attachmentContentType = (String) mms.get("attachmentContentType");

                    // If this is an image, upload to Firebase storage and keep a pointer in DB
                    final String attachmentUuidString;
                    if (!Objects.equals(attachment, "")) {
                      UUID attachmentId = UUID.randomUUID();
                      attachmentUuidString = attachmentId.toString();

                      StorageReference attachmentReference = storageReference.child(attachmentUuidString);
                      UploadTask uploadTask = attachmentReference.putBytes(attachment.getBytes());
                      uploadTask.addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception exception) {
                          Log.d(TAG, "Failed uploading photo for " + attachmentUuidString);
                        }
                      }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                        @Override
                        public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                          // taskSnapshot.getMetadata() contains file metadata such as size, content-type, and download URL.
                          Log.d(TAG, "Uploaded " + attachmentUuidString + " successfully");
                        }
                      });
                    } else {
                      attachmentUuidString = "";
                    }
                    Set<String> recipientIds = (Set<String>) mms.get("recipientIds");
                    Log.d(TAG, "New MMS message data:\n" +
                        "sender: " + sender +
                        " body: " + body +
                        " recipients: " + recipientIds);

                    // Write this new message to Firebase
                    Message message = new Message(
                        messageId,
                        senderPhoneNumber,
                        body,
                        attachmentUuidString,
                        attachmentContentType,
                        date);
                    databaseWrapper.messagesDBRef.child(conversationId + "/" + messageId).setValue(message);

                    Set<Long> recipientIdLongs = new HashSet<>();
                    for (String phoneNumberString : recipientIds) {
                      Map<String, Object> parsedPhoneNumber = contactUtil.parsePhoneNumber(phoneNumberString);
                      Long phoneNumber = (Long) parsedPhoneNumber.get("phoneNumber");
                      Integer countryCode = (Integer) parsedPhoneNumber.get("countryCode");

                      recipientIdLongs.add(phoneNumber);

                      ArrayList<String> attributesToQuery = new ArrayList<>();
                      attributesToQuery.add(ContactsContract.PhoneLookup._ID);
                      attributesToQuery.add(ContactsContract.PhoneLookup.DISPLAY_NAME);
                      attributesToQuery.add(ContactsContract.PhoneLookup.PHOTO_URI);
                      Map<String, String> contactAttributes = contactUtil.queryContact(
                          mContext,
                          phoneNumber,
                          attributesToQuery);
                      String recipientId = contactAttributes.get(ContactsContract.PhoneLookup._ID);
                      String recipientName = contactAttributes.get(ContactsContract.PhoneLookup.DISPLAY_NAME);
                      String recipientPhotoUri = contactAttributes.get(ContactsContract.PhoneLookup.PHOTO_URI);

                      // Append this conversation to this recipient, creating one if none exists
                      Map<String, Object> recipientUpdate = new HashMap<>();
                      if (recipientId != null) {
                        recipientUpdate.put("id", Long.valueOf(recipientId));
                      }
                      recipientUpdate.put("name", recipientName);
                      recipientUpdate.put("photoUri", recipientPhotoUri);
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

                    conversationUpdates.put("lastMessageContent", body);
                    conversationUpdates.put("lastMessageSender", senderPhoneNumber);
                    conversationUpdates.put("lastMessageTimestamp", date);
                    conversationUpdates.put("lastMessageAttachment", attachmentUuidString);
                    conversationUpdates.put("lastMessageAttachmentContentType", attachmentContentType);

                    databaseWrapper.conversationsDBRef
                        .child(conversationId.toString())
                        .updateChildren(conversationUpdates);
                  }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                  Log.w(TAG, "Firebase ValueEventListener Error", databaseError.toException());
                }
              });
        }
      }).start();
    }
    mmsCursor.close();
  }
}
