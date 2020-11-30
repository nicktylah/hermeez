package com.nicktylah.hermes;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.SignInButton;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.util.Calendar;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

  private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
  private final int MY_PERMISSIONS_REQUEST_SEND_SMS = 1;
  private final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 2;
  private final int SIGN_IN_RESULT = 2;
  private final int SIGN_OUT_RESULT = 3;
  private final Long SYNC_INTERVAL_MS = 72000000L; // 2 hours
  private final String TAG = "MainActivity";

  static final String SILENT_SIGN_IN = "SILENT_SIGN_IN";
  static final String SIGN_IN = "SIGN_IN";
  static final String SIGN_OUT = "SIGN_OUT";
  static final String SYNC_MESSAGES = "SYNC_MESSAGES";
  static final String MESSAGE_LISTEN = "MESSAGE_LISTEN";
  static final int UPDATE_PASSWORD_RESULT = 7;
  static final int PLAY_SERVICES = 7;

  static String firebaseUserId;

  private SignInButton signInBtn;
  private Button signOutBtn;
  private Button startMessageForwardingButton;
  private Button syncMessagesBtn;
  private Button updatePasswordBtn;
  private Long ownPhoneNumber;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, "onCreate called");
    super.onCreate(savedInstanceState);

    Intent intent = getIntent();
    if (Objects.equals(intent.getAction(), Sync.STOP_SYNC_ALL)) {
      Sync.syncingAll = false;
    }

    setContentView(R.layout.activity_main);
    signOutBtn = (Button)findViewById(R.id.signOutButton);
    startMessageForwardingButton = (Button)findViewById(R.id.startMessageForwardingButton);
    syncMessagesBtn = (Button)findViewById(R.id.syncMessagesButton);
    signInBtn = (SignInButton)findViewById(R.id.signInButton);
    updatePasswordBtn = (Button)findViewById(R.id.updatePasswordButton);

    findViewById(R.id.signInButton).setOnClickListener(this);
    findViewById(R.id.signOutButton).setOnClickListener(this);
    findViewById(R.id.syncMessagesButton).setOnClickListener(this);

    // Request SEND_SMS and/or READ_CONTACTS permissions if necessary
    if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.SEND_SMS) !=
        PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_SMS) !=
            PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(MainActivity.this,
          new String[]{
              Manifest.permission.SEND_SMS,
              Manifest.permission.READ_SMS,
              Manifest.permission.READ_CONTACTS,
              Manifest.permission.READ_PHONE_STATE},
          MY_PERMISSIONS_REQUEST_SEND_SMS);
    }

    startSignInActivity(true);
  }

  // Called by services who may need the UI of the MainActivity to request some permissions
  public static void requestPermissions(Activity activity, String[] permissions, int requestCode) {
    ActivityCompat.requestPermissions(
            activity,
            permissions,
            requestCode);
  }

  private void showPermissionDialog() {
    ActivityCompat.requestPermissions(
            this,
            new String[]{Manifest.permission.READ_SMS},
            123);
  }

  @Override
  public void onResume() {
    super.onResume();
  }

  @Override
  public void onClick(View v) {
    Log.d("MainActivity", "onClick");
    switch (v.getId()) {
      case R.id.signInButton:
        Log.d("MainActivity", "clicked sign in");
        startSignInActivity(false);
        break;
      case R.id.signOutButton:
        Log.d("MainActivity", "clicked sign out");
        startSignOutActivity();
        break;
      case R.id.startMessageForwardingButton:
        Log.d("MainActivity", "clicked start message forwarding");
        startMessageForwarding();
        break;
      case R.id.updatePasswordButton:
        Log.d("MainActivity", "clicked update password");
        Intent updatePasswordIntent = new Intent(this, UpdatePasswordActivity.class);
        startActivityForResult(updatePasswordIntent, UPDATE_PASSWORD_RESULT);
        break;
      case R.id.syncMessagesButton:
        Log.d("MainActivity", "clicked sync messages");
        Intent syncMessagesIntent = new Intent(this, Sync.class);
        syncMessagesIntent.setAction(SYNC_MESSAGES);
        startService(syncMessagesIntent);
        break;
    }
  }

  /**
   * Initialize our GoogleOAuth and Firebase Auth activity for a sign in.
   */
  private void startSignInActivity(boolean isSilent) {
    Intent signInIntent = new Intent(this, SignInActivity.class);
    Log.d(TAG, "starting sign in. Silent: " + isSilent);
    signInIntent.setAction(isSilent ? SILENT_SIGN_IN : SIGN_IN);
    startActivityForResult(signInIntent, SIGN_IN_RESULT);
  }

  /**
   * Initialize our GoogleOAuth and Firebase Auth activity for a sign out.
   */
  private void startSignOutActivity() {
    Intent signOutIntent = new Intent(this, SignInActivity.class);
    signOutIntent.setAction(SIGN_OUT);
    startActivityForResult(signOutIntent, SIGN_OUT_RESULT);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
    Log.i(TAG, "request code: " + requestCode + ", result code: " + resultCode + ", data: " + data);
    switch (requestCode) {
      case SIGN_IN_RESULT:
        if (resultCode == Activity.RESULT_CANCELED) {
          Log.i(TAG, "Failed to sign in");
        } else if (resultCode == Activity.RESULT_OK) {
          Log.i(TAG, "Signed in");
          Bundle bundle = data.getExtras();
          Log.i(TAG, "Sign In Result: " + bundle.get("firebaseUserId"));

          // Instantiate a databaseWrapper limited to this user's namespace
          firebaseUserId = (String) bundle.get("firebaseUserId");
          FirebaseDatabaseWrapper.init();

          // Send this device's token up, so we can FCM it later
          FirebaseDatabaseWrapper databaseWrapper = FirebaseDatabaseWrapper.getInstance();
          String fcmToken = FirebaseInstanceId.getInstance().getToken();
          if (databaseWrapper == null) {
            Log.w(TAG, "Firebase Database could not be initialized");
            return;
          }
          if (fcmToken == null) {
            Log.w(TAG, "Could not get FCM token");
            return;
          }

          Log.d(TAG, "Sending this device's token up");
          databaseWrapper.userTokenDBRef.setValue(fcmToken);

          // Show the sign out button, hide the sign in button
          signInBtn.setVisibility(View.INVISIBLE);
          updatePasswordBtn.setVisibility(View.VISIBLE);
          signOutBtn.setVisibility(View.VISIBLE);

          // Show the sync messages button
          syncMessagesBtn.setVisibility(View.VISIBLE);
          startMessageForwardingButton.setVisibility(View.VISIBLE);

          startMessageForwarding();

          // Set up a repeating sync
          Intent syncIntent = new Intent(this, Sync.class);
          syncIntent.setAction(MainActivity.SYNC_MESSAGES);
          PendingIntent pendingIntent = PendingIntent.getService(this, 22, syncIntent, 0);

          // Sync now
          if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.READ_SMS) !=
                  PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No READ_SMS permission granted");
            return;
          }
          startService(syncIntent);

          AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
          alarmManager.setRepeating(
              AlarmManager.RTC,
              Calendar.getInstance().getTimeInMillis() + SYNC_INTERVAL_MS,
              SYNC_INTERVAL_MS,
              pendingIntent);

          // Lookup our own phone number
          String ownPhoneNumberString = lookupOwnPhoneNumber(6);
          if (Objects.equals(ownPhoneNumberString, "")) {
            return;
          }

          try {
            ownPhoneNumber = phoneNumberUtil.parse(ownPhoneNumberString, "US").getNationalNumber();
          } catch (NumberParseException | NullPointerException e) {
            Log.w(TAG, "Could not determine or set this phone's own number from: "
                + ownPhoneNumberString);
            ownPhoneNumber = -1L;
          }
          databaseWrapper.ownPhoneNumberDBRef.setValue(ownPhoneNumber);

        }
        break;
      case SIGN_OUT_RESULT:
        if (resultCode == Activity.RESULT_CANCELED) {
          Log.i(TAG, "Failed to sign out");
        } else if (resultCode == Activity.RESULT_OK) {
          Log.i(TAG, "Signed out");

          // Show the sign in button, hide the sign out button
          signOutBtn.setVisibility(View.INVISIBLE);
          updatePasswordBtn.setVisibility(View.INVISIBLE);
          startMessageForwardingButton.setVisibility(View.INVISIBLE);
          syncMessagesBtn.setVisibility(View.INVISIBLE);
          signInBtn.setVisibility(View.VISIBLE);
        }
        break;
    }
  }

  private String lookupOwnPhoneNumber(int retriesLeft) {
    if (retriesLeft <= 0) {
      return "";
    }

    if (ActivityCompat.checkSelfPermission(
        MainActivity.this,
        Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(MainActivity.this,
          new String[]{
              Manifest.permission.READ_PHONE_STATE},
          MY_PERMISSIONS_REQUEST_SEND_SMS);
    }

    // Lookup this phone's phone number
    String line1 = ((TelephonyManager) getApplicationContext()
        .getSystemService(Context.TELEPHONY_SERVICE))
        .getLine1Number();
    if (line1 != null && !Objects.equals(line1, "")) {
      return line1;
    }

    try {
      Thread.sleep(100);
    } catch (InterruptedException ignored) {}

    return lookupOwnPhoneNumber(retriesLeft - 1);
  }

  /**
   * Callback to our request for SMS Permissions.
   * @param requestCode
   * @param permissions
   * @param grantResults
   */
  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         @NonNull String permissions[],
                                         @NonNull int[] grantResults) {
      Log.d(TAG, "Request Permissions Result" + requestCode);
    switch (requestCode) {
      case MY_PERMISSIONS_REQUEST_SEND_SMS:
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
          Toast.makeText(
              MainActivity.this,
              "SMS Permissions are required to use this application. Please restart and enable" +
                  "them to use Android Messages",
              Toast.LENGTH_LONG).show();
        }
        break;
      case MY_PERMISSIONS_REQUEST_READ_CONTACTS:
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
          Toast.makeText(
              MainActivity.this,
              "Read Contacts Permissions are required to use this application. Please restart and" +
                  "enable them to use Android Messages",
              Toast.LENGTH_LONG).show();
        }
    }
  }

  /**
   * Sets up a ContentObserver for changes to the sms table
   */
  private void startMessageForwarding() {
    Intent messageListenIntent = new Intent(this, ForegroundMessageService.class);
    messageListenIntent.setAction(MESSAGE_LISTEN);
    startService(messageListenIntent);
  }
}

