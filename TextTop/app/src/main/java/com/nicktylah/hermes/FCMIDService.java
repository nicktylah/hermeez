package com.nicktylah.hermes;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

/**
 * Handles the creation, rotation, and updating of registration tokens. This is required for sending
 * to specific devices or for creating device groups.
 */
public class FCMIDService extends FirebaseInstanceIdService {

  private final String TAG = "FCMIDService";

  @Override
  public void onTokenRefresh() {
    // Get updated InstanceID token.
    String refreshedToken = FirebaseInstanceId.getInstance().getToken();
    Log.d(TAG, "Refreshed token: " + refreshedToken);

    // If you want to send messages to this application instance or
    // manage this apps subscriptions on the server side, send the
    // Instance ID token to your app server.
    sendRegistrationToServer(refreshedToken);
  }

  private void sendRegistrationToServer(String token) {
    FirebaseDatabaseWrapper databaseWrapper = FirebaseDatabaseWrapper.getInstance();
    if (databaseWrapper == null) {
      FirebaseDatabaseWrapper.init();
      databaseWrapper = FirebaseDatabaseWrapper.getInstance();
    }
    databaseWrapper.userTokenDBRef.setValue(token);
  }
}
