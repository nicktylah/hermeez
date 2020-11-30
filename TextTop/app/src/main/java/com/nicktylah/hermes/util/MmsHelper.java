package com.nicktylah.hermes.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.net.Uri;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Mms.Addr;
import android.util.Base64;
import android.util.Log;

import com.google.android.mms.pdu_alt.PduHeaders;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Helper that gathers necessary information about MMS messages
 */
public class MmsHelper {

  private final String TAG = "MmsHelper";
  private final ByteUtils byteUtils = new ByteUtils();

  private Context mContext;

  public MmsHelper(Context context) {
    this.mContext = context;
  }

  /**
   * Takes in an MMS message's ID, and gathers all the necessary information about it
   * @param messageId
   * @return
   */
  public Map<String, Object> parseMms(Long messageId) {
    Map<String, Object> mms = new HashMap<>();

    String body = "";
    byte[] attachment = new byte[0];
    String attachmentContentType = "";

    // Get Parts
    Uri uriMMSPart = Uri.parse("content://mms/part");
    Cursor curPart = mContext.getContentResolver().query(
        uriMMSPart,
        new String[]{Mms.Part.CONTENT_TYPE, Mms.Part._ID},
        "mid = " + messageId,
        null,
        "_id");
    assert curPart != null;

    while (curPart.moveToNext()) {
      String contentType = curPart.getString(curPart.getColumnIndex(Mms.Part.CONTENT_TYPE));
      String partId = curPart.getString(curPart.getColumnIndex(Mms.Part._ID));
//      Log.d(TAG, "Content type: " + contentType);
      switch (contentType.toLowerCase()) {

        // Disregard the SMIL portion of the MMS
        case ("application/smil"):
          break;
        case ("text/plain"):
          byte[] messageData = readMMSPart(partId);
          if (messageData != null && messageData.length > 0) {
            body = new String(messageData);
          }

          if (Objects.equals(body, "")) {
            Cursor curPart1 = mContext.getContentResolver().query(
                uriMMSPart,
                new String[]{Mms.Part.TEXT},
                "mid = " + messageId + " and _id =" + partId,
                null,
                "_id");
            assert curPart1 != null;
            curPart1.moveToLast();
            body = curPart1.getString(curPart1.getColumnIndex(Mms.Part.TEXT));
            curPart1.close();
          }
          break;

        // Image
        case ("image/jpeg"):
        case ("image/jpg"):
        case ("image/png"):
        case ("image/gif"):
        case ("image/bmp"):
          attachmentContentType = contentType;
          attachment = readMMSPart(partId);
//          byte[] compressedImgData = byteUtils.compress(imgData, Deflater.BEST_COMPRESSION);
          break;

        // Video
        case ("video/mpeg"):
        case ("video/mp4"):
        case ("video/quicktime"):
        case ("video/webm"):
        case ("video/3gpp"):
        case ("video/3gpp2"):
        case ("video/3gpp-tt"):
        case ("video/H261"):
        case ("video/H263"):
        case ("video/H263-1998"):
        case ("video/H263-2000"):
        case ("video/H264"):
          attachmentContentType = contentType;
          attachment = readMMSPart(partId);
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
          break;
      }
    }

    curPart.close();

    // Get Recipients
    String senderString = "content://mms/" + messageId + "/addr";
    Uri uri = Uri.parse(senderString);
    String[] senderColumns = new String[]{Addr.ADDRESS, Addr.TYPE};
    Cursor senderCursor = mContext.getContentResolver().query(
        uri,
        senderColumns, // SELECT
        null,
        null,
        null);
    String sender = "";
    Set<String> recipientIds = new HashSet<>();
    try {
      assert senderCursor != null;
      while (senderCursor.moveToNext()) {
//        String[] cols = senderCursor.getColumnNames();
//        for (String col : cols) {
//          Log.d(TAG, col + ": " + senderCursor.getString(senderCursor.getColumnIndex(col)));
//        }
        String address =
            senderCursor.getString(senderCursor.getColumnIndex(Addr.ADDRESS));
        Integer type =
            Integer.parseInt(senderCursor.getString(senderCursor.getColumnIndex(Addr.TYPE)));

        // Means this address sent the message
        if (type == PduHeaders.FROM) {
          sender = address;
        }
        recipientIds.add(address);
      }
    } catch(NullPointerException e) {
      Log.e(TAG, "Could not find MMS message addr");
      return mms;
    }

    senderCursor.close();
    mms.put("sender", sender);
    mms.put("recipientIds", recipientIds);
    mms.put("attachmentContentType", attachmentContentType);
    mms.put("body", byteUtils.encrypt(mContext, body));
    if (attachment.length > 0) {
      String encryptedAttachment = byteUtils.encrypt(
          mContext,
          Base64.encodeToString(attachment, Base64.DEFAULT));
      mms.put("attachment", encryptedAttachment);
    } else {
      mms.put("attachment", "");
    }
    return mms;
  }

  /**
   * Handles getting a specific MMS message's part
   * @param partId
   * @return
   */
  private byte[] readMMSPart(String partId) {
    byte[] partData = new byte[0];
    Uri partURI = Uri.parse("content://mms/part/" + partId);
    InputStream is;

    try {
      ContentResolver mContentResolver = mContext.getContentResolver();
      is = mContentResolver.openInputStream(partURI);
      if (is == null) {
        return new byte[0];
      }

      partData = IOUtils.toByteArray(is);
      is.close();
    } catch (IOException ignored) {}

    return partData;
  }
}
