package com.nicktylah.hermes;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Objects;

/**
 * Handles requests to update a password. We're required to link Google OAuth users (the preferred
 * sign in method for this app) with email/password users (for encryption)
 */
public class UpdatePasswordActivity extends AppCompatActivity {

  private final String TAG = "UpdatePasswordActivity";

  private EditText passwordInput1;
  private EditText passwordInput2;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    Log.d(TAG, "onCreate called");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.update_password);

    passwordInput1 = (EditText) findViewById(R.id.passwordInput1);
    passwordInput2 = (EditText) findViewById(R.id.passwordInput2);

    Button confirmButton = (Button) findViewById(R.id.confirmButton);

    confirmButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        Log.d(TAG, "clicked confirm");
        updatePassword();
      }
    });
  }

  private void updatePassword() {
    if (!Objects.equals(passwordInput1.getText().toString(), passwordInput2.getText().toString())) {
      Toast.makeText(UpdatePasswordActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
      return;
    }

    if (passwordInput1.getText().toString().length() < 6) {
      Toast.makeText(UpdatePasswordActivity.this, "Passwords must be at least 6 characters", Toast.LENGTH_SHORT).show();
      return;
    }

    FirebaseAuth mAuth = FirebaseAuth.getInstance();
    FirebaseUser user = mAuth.getCurrentUser();
    if (user == null) {
      Toast.makeText(UpdatePasswordActivity.this, "Not signed in. Please sign in and try again", Toast.LENGTH_SHORT).show();
      return;
    }

    String email = user.getEmail();
    if (email == null) {
      Toast.makeText(UpdatePasswordActivity.this, "No email found. Please sign in and try again", Toast.LENGTH_SHORT).show();
      return;
    }

    final String password = passwordInput1.getText().toString();
    AuthCredential credential = EmailAuthProvider.getCredential(
        email,
        password);
    mAuth.getCurrentUser().linkWithCredential(credential)
        .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
          @Override
          public void onComplete(@NonNull Task<AuthResult> task) {
            Log.d(TAG, "linkWithCredential:onComplete:" + task.isSuccessful());

            if (!task.isSuccessful()) {
              Log.e(TAG, "err", task.getException());
              Toast.makeText(UpdatePasswordActivity.this, "Authentication failed.",
                  Toast.LENGTH_SHORT).show();
              finish();
            }

            // Write key to private storage
            try {
              OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                  getApplicationContext().openFileOutput(
                      getString(R.string.hermes_pw),
                      Context.MODE_PRIVATE));
              outputStreamWriter.write(password);
              outputStreamWriter.close();
            } catch (IOException e) {
              Log.e("Exception", "File write failed: " + e.toString());
            }

            passwordInput1.setText("");
            passwordInput2.setText("");
            finish();
          }
        });
  }
}
