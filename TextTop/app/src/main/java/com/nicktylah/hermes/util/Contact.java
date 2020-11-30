package com.nicktylah.hermes.util;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static android.provider.BaseColumns._ID;

/**
 * Provides utility methods for accessing information about an Android Contact.
 */
public class Contact {

  private static final Contact contact = new Contact();

  private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
  private final ByteUtils utils = new ByteUtils();
  private final String TAG = "Contact";
  private StorageReference storageReference;

  public Contact() {
    try {
      storageReference = FirebaseStorage.getInstance()
          .getReference("user")
          .child(FirebaseAuth.getInstance().getCurrentUser().getUid())
          .child("photo-uris");
    } catch (NullPointerException e) {
      Log.d(TAG, "Could not get the current firebase user");
    }
  }

  /**
   * Return a singleton
   *
   * @return Contact
   */
  public static Contact getInstance() {
    return contact;
  }

  /**
   * Helper method for accessing Android Contact data
   *
   * @param mContext   Required for accessing data
   * @param phoneNumber The identifier for this contact (Phone Number)
   * @param attributes A list of attributes to query for
   * @return Map<AttributeName, Value>
   */
  public Map<String, String> queryContact(
      Context mContext,
      final Long phoneNumber,
      ArrayList<String> attributes) {

    // Compose url for looking up the contact
    Uri uri = Uri.withAppendedPath(
        PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(phoneNumber.toString()));

    // Add _id, we may need it for querying PHOTO_URI
    if (!attributes.contains("_id")) {
      attributes.add(_ID);
    }
    String[] attributesArray = new String[attributes.size()];
    attributesArray = attributes.toArray(attributesArray);

    Cursor cursor = mContext.getContentResolver().query(
        uri,
        attributesArray,
        null,
        null,
        null);
    if (cursor == null) {
      return null;
    }

    final Map<String, String> results = new HashMap<>();

    final String TAG = "Util.Contact";
    if (cursor.moveToFirst()) {
      String contactId = cursor.getString(cursor.getColumnIndex(_ID));
      for (final String attribute : attributes) {
        String base64Photo;

        // Special case for photo uris, we must query separately and transform
        if (Objects.equals(attribute, "photo_uri")) {
          InputStream inputStream = Contacts.openContactPhotoInputStream(
              mContext.getContentResolver(),
              ContentUris.withAppendedId(Contacts.CONTENT_URI, Long.valueOf(contactId)));
          byte[] photo;
          try {
//            photo = utils.compress(IOUtils.toByteArray(inputStream), Deflater.BEST_COMPRESSION);
            photo = IOUtils.toByteArray(inputStream);

            // Encode it to Base64/encrypt
            base64Photo = utils.encrypt(mContext, Base64.encodeToString(photo, Base64.DEFAULT));

            String identifier = phoneNumber.toString();
            StorageReference photoUriReference = storageReference.child(identifier);
            UploadTask uploadTask = photoUriReference.putBytes(base64Photo.getBytes("UTF-8"));
            uploadTask.addOnFailureListener(new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception exception) {
                Log.d(TAG, "Failed uploading photo for " + phoneNumber);
              }
            }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
              @Override
              public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Log.d(TAG, "Uploaded " + phoneNumber);
              }
            });

          } catch (Exception e) {
            Log.w(TAG, "Failed to handle contact photo");
          }
        } else {
          base64Photo = cursor.getString(cursor.getColumnIndex(attribute));
          results.put(attribute, base64Photo);
        }
      }
    } else {
      Log.d(TAG, "No results for identifier: " + phoneNumber);
    }

    cursor.close();
    return results;
  }

  /**
   * Parses a string phone number, setting defaults in failure cases.
   *
   * @param phoneNumberString
   * @return
   */
  public Map<String, Object> parsePhoneNumber(String phoneNumberString) {
    Long phoneNumber;
    Integer countryCode;
    try {
      PhoneNumber parsedPhoneNumber = phoneNumberUtil.parse(phoneNumberString, "US");
      phoneNumber = parsedPhoneNumber.getNationalNumber();
      countryCode = parsedPhoneNumber.getCountryCode();
    } catch (NumberParseException ignored) {
      int DEFAULT_COUNTRY_CODE = 1;
      countryCode = DEFAULT_COUNTRY_CODE;
      Log.w(TAG, "Could not get this device's number from: " + phoneNumberString +
          ", using raw value");
      try {
        phoneNumber = Long.valueOf(phoneNumberString);
      } catch (NumberFormatException ignored2) {
        Log.w(TAG, "Couldn't parse as a long, defaulting to 0");
        countryCode = DEFAULT_COUNTRY_CODE;
        phoneNumber = 0L;
      }
    }
    Map<String, Object> parsedPhoneNumber = new HashMap<>();
    parsedPhoneNumber.put("phoneNumber", phoneNumber);
    parsedPhoneNumber.put("countryCode", countryCode);
    return parsedPhoneNumber;
  }
}
