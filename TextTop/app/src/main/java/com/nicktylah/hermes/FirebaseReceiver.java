package com.nicktylah.hermes;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Base64;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.mms.InvalidHeaderValueException;
import com.google.android.mms.pdu_alt.EncodedStringValue;
import com.google.android.mms.pdu_alt.PduBody;
import com.google.android.mms.pdu_alt.PduComposer;
import com.google.android.mms.pdu_alt.PduPart;
import com.google.android.mms.pdu_alt.SendReq;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Listens for new Firebase Cloud Messages, sends SMS/MMS based on the new data
 */
public class FirebaseReceiver extends FirebaseMessagingService {

  private final String TAG = "FirebaseReceiver";
  private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

  private FirebaseDatabaseWrapper databaseWrapper;
  private Long ownPhoneNumber;

  @Override
  public void onCreate() {
    super.onCreate();
    if (databaseWrapper == null) {
      databaseWrapper = FirebaseDatabaseWrapper.getInstance();
    }

    if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) {
      Log.d(TAG, "Permission(s) not granted");
      return;
    }
    String line1 = ((TelephonyManager) getApplicationContext()
        .getSystemService(Context.TELEPHONY_SERVICE))
        .getLine1Number();
    Log.d(TAG, "own phone: " + line1);
    try {
      ownPhoneNumber = phoneNumberUtil.parse(line1, "US").getNationalNumber();
      databaseWrapper.ownPhoneNumberDBRef.setValue(ownPhoneNumber);
    } catch (NumberParseException | NullPointerException e) {
      Log.w(TAG, "Could not determine or set this phone's own number");
      ownPhoneNumber = -1L;
    }
  }

  @Override
  public void onMessageReceived(RemoteMessage remoteMessage) {
    Log.d(TAG, "From: " + remoteMessage.getFrom());

    // Check if message contains a data payload.
    if (remoteMessage.getData().size() > 0) {
      Map<String, String> payload = remoteMessage.getData();
      Log.d(TAG, "Message data payload: " + payload);

      Long[] recipientIds;
      String content;
      String attachmentPointer;
      String attachmentContentType;
      String[] recipientIdStrings;

      try {
        recipientIdStrings = payload.get("recipientIds").split(",");
        recipientIds = new Long[recipientIdStrings.length];
        for (int i = 0; i < recipientIdStrings.length; i++) {
          recipientIds[i] = Long.valueOf(recipientIdStrings[i]);
        }

        content = payload.get("content");
        attachmentPointer = payload.get("attachmentPointer");
        attachmentContentType = payload.get("attachmentContentType");

        if (payload.get("recipientIds") == null || recipientIds.length == 0 || content == null) {
          throw new IllegalArgumentException("Required key not found or malformed");
        }
      } catch (IllegalArgumentException e) {
        Log.w(TAG, "Message contained improper data", e);
        return;
      }

      if (Objects.equals(attachmentPointer, "") && recipientIds.length == 1) {
        sendSms(recipientIds, content);
      } else {
        if (!Objects.equals(attachmentPointer, "")) {
          getAttachmentAndSendMms(
              recipientIdStrings,
              content,
              attachmentPointer,
              attachmentContentType);
        } else {
          sendMmsViaSmsManager(recipientIdStrings, content);
        }
      }
    } else {
      Log.w(TAG, "FCM Message did not contain the necessary payload items");
    }

    // Check if message contains a notification payload.
    if (remoteMessage.getNotification() != null) {
      Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
    }
  }

  /**
   * Sends an SMS with the provided body to the provided phone number. Notifies the provided
   * receiver when SMS_SENT completes
   *
   * @param phoneNumbers
   * @param body
   */
  private void sendSms(final Long[] phoneNumbers, String body) {
    if (phoneNumbers.length > 1) {
      Log.e(TAG, "Cannot send SMS to multiple recipients");
      return;
    }

    try {
      SmsManager sms = SmsManager.getDefault();
      ArrayList<String> multipartBody = sms.divideMessage(body);
      sms.sendMultipartTextMessage(phoneNumbers[0].toString(), null, multipartBody, null, null);
    } catch (Exception e) {
      Log.e(TAG, "Could not send SMS", e);
    }
  }

  private void getAttachmentAndSendMms(
      final String[] phoneNumbers,
      final String body,
      final String attachmentPointer,
      final String attachmentContentType
  ) {
    RequestQueue queue = Volley.newRequestQueue(this);
    StringRequest stringRequest = new StringRequest(
        Request.Method.GET,
        attachmentPointer,
        new Response.Listener<String>() {
          // @Override
          public void onResponse(String response) {
//              final byte[] attachmentBytes = byteUtils.decompress(
//                  Base64.decode(response, Base64.DEFAULT));
            final byte[] attachmentBytes = Base64.decode(response, Base64.DEFAULT);
            Log.d(TAG, "Received attachment: " + response);
            byte[] pduBytes = composePdu(phoneNumbers, body, attachmentBytes, attachmentContentType);
            savePduAndSendMms(pduBytes);
          }
        },
        new Response.ErrorListener() {
          @Override
          public void onErrorResponse(VolleyError e) {
            Log.e(TAG, "Error requesting attachment", e);
          }
        });

    // Add the request to the RequestQueue.
    queue.add(stringRequest);
  }

  private void sendMmsViaSmsManager(final String[] phoneNumbers, final String body) {
    byte[] pduBytes = composePdu(phoneNumbers, body, new byte[]{}, null);
    savePduAndSendMms(pduBytes);
  }

  private void savePduAndSendMms(byte[] pdu) {
    File attachment = new File(getExternalCacheDir(), UUID.randomUUID().toString());
    FileOutputStream outputStream;
    try {
      outputStream = new FileOutputStream(attachment);
      outputStream.write(pdu);
      outputStream.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

    SmsManager sms = SmsManager.getDefault();
    try {
      sms.sendMultimediaMessage(getApplicationContext(), Uri.fromFile(attachment), null, null, null);
    } catch (IllegalArgumentException e) {
      Log.e(TAG, "Could not send MMS", e);
    }
  }

  private byte[] composePdu(
      final String[] phoneNumbers,
      final String body,
      final byte[] attachmentBytes,
      final String attachmentContentType) {
    final SendReq sendRequest = new SendReq();
    sendRequest.setFrom(new EncodedStringValue(ownPhoneNumber.toString()));
    try {
      sendRequest.setMessageType(128);
      sendRequest.setMmsVersion(18);
    } catch (InvalidHeaderValueException e) {
      e.printStackTrace();
    }

    for (String phoneNumber : phoneNumbers) {
      EncodedStringValue[] recipient = EncodedStringValue.extract(phoneNumber);
      sendRequest.addTo(recipient[0]);
    }

    final PduBody pduBody = new PduBody();

    // Send the text part, if it exists
    if (!Objects.equals(body, "")) {
      final PduPart bodyPdu = new PduPart();
      bodyPdu.setName("body".getBytes());
      bodyPdu.setContentType("text/plain".getBytes());
      bodyPdu.setData(body.getBytes());
      pduBody.addPart(bodyPdu);
    }

    // Send the attachment part, if it exists
    if (attachmentBytes.length > 0) {
      final PduPart attachmentPdu = new PduPart();
      attachmentPdu.setName("attachment".getBytes());
      attachmentPdu.setContentType(attachmentContentType.getBytes());
      attachmentPdu.setData(attachmentBytes);
      pduBody.addPart(attachmentPdu);
    }

    sendRequest.setBody(pduBody);
    return new PduComposer(getApplicationContext(), sendRequest).make();
  }
}

