package com.nicktylah.hermes;

import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Responsible for all interactions with Firebase. Limited in scope to whatever user is currently
 * logged in.
 */
public class FirebaseDatabaseWrapper {

  private static FirebaseDatabaseWrapper instance;

  public static synchronized FirebaseDatabaseWrapper getInstance() {
    if (instance == null) {
      Log.w("WrapperStatic", "Not initialized, call init first");
    }
    return instance;
  }

  DatabaseReference userTokenDBRef;
  DatabaseReference ownPhoneNumberDBRef;
  DatabaseReference conversationsDBRef;
  DatabaseReference messagesDBRef;
  DatabaseReference recipientsDBRef;

  static void init() {
    String TAG = "FirebaseDatabaseWrapper";
    Log.d(TAG, "Initializing database wrapper...");
    if (instance == null) {
      instance = new FirebaseDatabaseWrapper();
    }
    FirebaseDatabase mDatabase = FirebaseDatabase.getInstance();
//    mDatabase.setLogLevel(Logger.Level.DEBUG);
    DatabaseReference database = mDatabase.getReference();
    instance.userTokenDBRef = database.child("/users/" + MainActivity.firebaseUserId + "/fcm_token");
    instance.ownPhoneNumberDBRef = database.child("/users/" + MainActivity.firebaseUserId + "/recipients/self");
    instance.conversationsDBRef = database.child("/users/" + MainActivity.firebaseUserId + "/conversations");
    instance.messagesDBRef = database.child("/users/" + MainActivity.firebaseUserId + "/messages");
    instance.recipientsDBRef = database.child("/users/" + MainActivity.firebaseUserId + "/recipients");
  }

  private FirebaseDatabaseWrapper() {
  }
}
