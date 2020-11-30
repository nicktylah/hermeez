package com.nicktylah.hermes;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.nicktylah.hermes.util.ByteUtils;

import java.util.Objects;

import static com.nicktylah.hermes.MainActivity.UPDATE_PASSWORD_RESULT;

/**
 * Handles signing the user in with Google's API
 */
public class SignInActivity extends AppCompatActivity implements OnConnectionFailedListener {

  private final int RC_SIGN_IN = 1;
  private final String TAG = "SignInActivity";

  private GoogleApiClient mGoogleApiClient;
  private FirebaseAuth mAuth;
  private FirebaseAuth.AuthStateListener mAuthListener;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Configure sign-in to request the user's ID, email address, and basic
    // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(getString(R.string.default_web_client_id))
        .requestEmail()
        .build();

    // Build a GoogleApiClient with access to the Google Sign-In API and the
    // options specified by gso.
    mGoogleApiClient = new GoogleApiClient.Builder(this)
        .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
        .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
        .build();

    mAuth = FirebaseAuth.getInstance();

    mAuthListener = new FirebaseAuth.AuthStateListener() {
      @Override
      public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
        final FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user != null) {
          // User is signed in
          Log.d(TAG, "onAuthStateChanged:signed_in:" + user.getUid());
        } else {
          // User is signed out
          Log.d(TAG, "onAuthStateChanged:signed_out");
        }
      }
    };

    Intent i = getIntent();
    Log.d(TAG, "SignInActivity called with " + i.getAction());

    switch (i.getAction()) {
      case MainActivity.SILENT_SIGN_IN:
        OptionalPendingResult<GoogleSignInResult> pendingResult = Auth.GoogleSignInApi
            .silentSignIn(mGoogleApiClient);

        if (pendingResult.isDone()) {
          handleSignOnResult(pendingResult.get());
          Log.d(TAG, "silent sign on done. isConnected: " + mGoogleApiClient.isConnected());
        } else {
          // There's no immediate result ready, display some progress indicator and waits for the
          // async callback.
          pendingResult.setResultCallback(new ResultCallback<GoogleSignInResult>() {
            @Override
            public void onResult(@NonNull GoogleSignInResult result) {
              handleSignOnResult(result);
            }
          });
        }
        break;
      case MainActivity.SIGN_IN:
        // TODO(nick): Sign out explicitly/figure out connection issues so new user can log in
//        Auth.GoogleSignInApi.signOut(mGoogleApiClient);
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
        break;
      case MainActivity.SIGN_OUT:
        signOut();
        break;
    }

  }

  private void handleSignOnResult(GoogleSignInResult result) {
    if (!result.isSuccess()) {
      Log.d(TAG, "user not signed in");
      setResult(RESULT_CANCELED);
      finish();
    } else {
      Log.d(TAG, "user signed in, performing firebase auth..");
      firebaseAuthWithGoogle(result.getSignInAccount());
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
    super.onActivityResult(requestCode, resultCode, data);

    // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
    if (requestCode == RC_SIGN_IN) {
      GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
      Log.d(TAG, "GoogleAPI result: " + result.getStatus());
      if (result.isSuccess()) {
        Toast.makeText(
            SignInActivity.this,
            "Google Authentication succeeded!",
            Toast.LENGTH_SHORT).show();
        // Google Sign In was successful, authenticate with Firebase
        GoogleSignInAccount account = result.getSignInAccount();
        firebaseAuthWithGoogle(account);
      } else {
        // Google Sign In failed, update UI appropriately
        Toast.makeText(
            SignInActivity.this,
            "Google Authentication failed.",
            Toast.LENGTH_LONG).show();
        final Intent resultIntent = new Intent();
        setResult(RESULT_CANCELED, resultIntent);
        finish();
      }
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    mAuth.addAuthStateListener(mAuthListener);
  }

  @Override
  public void onStop() {
    super.onStop();
    if (mAuthListener != null) {
      mAuth.removeAuthStateListener(mAuthListener);
    }
  }

  /**
   * Signs a user into to Firebase with successful Google OAuth creds.
   * @param acct GoogleSignInAccount
   */
  private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
    AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
    mAuth.signInWithCredential(credential)
        .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
          @Override
          public void onComplete(@NonNull Task<AuthResult> task) {
            Log.d(TAG, "signInWithCredential:onComplete:" + task.isSuccessful());
            Log.d(TAG, "auth result:" + task.getResult());
            Log.d(TAG, "user:" + task.getResult().getUser());

            // If sign in fails, display a message to the user. If sign in succeeds
            // the auth state listener will be notified and logic to handle the
            // signed in user can be handled in the listener.
            if (!task.isSuccessful() || mAuth.getCurrentUser() == null) {
              Log.w(TAG, "signInWithCredential", task.getException());
              Toast.makeText(
                  SignInActivity.this,
                  "Authentication failed.",
                  Toast.LENGTH_SHORT).show();
              signOut();
            } else {
              final FirebaseUser user = mAuth.getCurrentUser();
              Log.d(TAG, "signInWithCredential:onComplete:" + user.getUid());
              final Intent resultIntent = new Intent().putExtra("firebaseUserId", user.getUid());
              setResult(RESULT_OK, resultIntent);

              // Check if we've already set a key for this user
              String key = new ByteUtils().getKey(getApplicationContext());
              if (Objects.equals(key, getString(R.string.default_hermes_pw))) {
                Log.d(TAG, "Key not set. Starting update password activity");
                Toast.makeText(
                    getApplicationContext(),
                    "No key detected. Until you set one, your data will be poorly protected!",
                    Toast.LENGTH_LONG).show();
                Intent updatePasswordIntent = new Intent(
                    getApplicationContext(),
                    UpdatePasswordActivity.class);
                startActivityForResult(updatePasswordIntent, UPDATE_PASSWORD_RESULT);
              }
            }
            finish();
          }
        });
  }

  /**
   * Signs the current user out of Firebase and Google's OAuth
   */
  private void signOut() {
    Log.d(TAG, "signOut called");

    // Firebase sign out
    mAuth.signOut();
    setResult(RESULT_OK);

    mGoogleApiClient.connect();
    mGoogleApiClient.registerConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
      @Override
      public void onConnected(@Nullable Bundle bundle) {

        // Google sign out
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
            new ResultCallback<Status>() {
              @Override
              public void onResult(@NonNull Status status) {
                if (status.isSuccess()) {
                  Toast.makeText(
                      SignInActivity.this,
                      "Signed out",
                      Toast.LENGTH_SHORT).show();
                  mAuth.signOut();
                  finish();
                } else {
                  setResult(RESULT_CANCELED);
                  finish();
                }
              }
            });
      }

      @Override
      public void onConnectionSuspended(int i) {
        Log.d(TAG, "Google API Client Connection Suspended");
      }
    });
  }

  @Override
  public void onConnectionFailed(ConnectionResult result) {
    Log.e(TAG, "Error with play services: " + result.getErrorMessage());
    int error = result.hashCode();
    if (error == ConnectionResult.SERVICE_MISSING ||
        error == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED ||
        error == ConnectionResult.SERVICE_DISABLED) {
      Dialog errorDialog = GoogleApiAvailability.getInstance().getErrorDialog(
          this,
          result.hashCode(),
          MainActivity.PLAY_SERVICES);

      if (errorDialog != null) {
        errorDialog.show();
      }
    }
  }
}
