package nicktylah.hermes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.activity_entry.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.async
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.warn
import me.pushy.sdk.Pushy

/**
 * Kicks off the app, showing sign in (with google) and sign out buttons
 */
class EntryActivity : AppCompatActivity(), AnkoLogger {

  private val myPermissionsRequest = 123
  private val rcSignIn = 456

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Handle incoming push notifications (to send messages)
    Pushy.listen(this)
    setContentView(R.layout.activity_entry)

    val auth = FirebaseAuth.getInstance()
    if (auth.currentUser != null) {
      // Already signed in
      val user = auth.currentUser!!
      info("Already signed in:\nuser: ${user.email}\n${user.displayName}\n${user.uid}")
      setup()
        // Don't resubmit Pushy token in this case. FCM tokens may need to be re-uploaded
//      pushyToken()
      startActivity(Intent(this, SettingsActivity::class.java))
    }

    sign_in_button.setOnClickListener { signIn() }
    email_sign_out_button.setOnClickListener { signOut() }
  }

  @Override
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (data == null) {
      return
    }

    if (requestCode == rcSignIn) {
      val response = IdpResponse.fromResultIntent(data)

      if (resultCode == RESULT_OK) {
        // Successfully signed in
        setup()
        pushyToken()
        startActivity(Intent(this, SettingsActivity::class.java))
      } else {
        // Sign in failed, check response for error code
        if (response != null) {
          warn("Auth failed. Error code: ${response.errorCode}")
        }
      }
    }
  }

  private fun pushyToken() {
    async(CommonPool) {
      val pushyDeviceToken = Pushy.register(applicationContext)
      info("Have token (Pushy): $pushyDeviceToken, previous token: ${Util.getFcmToken(applicationContext)}")
      if (pushyDeviceToken != null && pushyDeviceToken != "" && pushyDeviceToken != Util.getFcmToken(applicationContext)) {
        info("Sending token up")
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        Util.postPushyTokenAsync(pushyDeviceToken, uid) { _, response, _ -> info("Sent Pushy token:\nStatus: ${response.statusCode}\n$pushyDeviceToken")}
        Util.setPushyToken(applicationContext, pushyDeviceToken)
      }
    }
  }

  private fun signIn() {
    val providers = listOf(AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build())

    // Create and launch sign-in intent
    startActivityForResult(
        AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setIsSmartLockEnabled(
                !BuildConfig.DEBUG /* credentials */,
                true /* hints */)
            .build(),
        rcSignIn)
  }

  private fun signOut() {
    Util.cancelMmsSmsJob(applicationContext)
    AuthUI.getInstance()
        .signOut(this)
        .addOnCompleteListener {
          info("Sign out complete")
          Toast.makeText(applicationContext, "Signed out", Toast.LENGTH_SHORT).show()
        }
        .addOnFailureListener { warn("Sign out failed") }
  }

  private fun setup() {
    if (
        ActivityCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
          this,
          arrayOf(
              Manifest.permission.RECEIVE_SMS,
              Manifest.permission.WRITE_EXTERNAL_STORAGE,
              Manifest.permission.SEND_SMS,
              Manifest.permission.READ_CONTACTS,
              Manifest.permission.READ_SMS),
          myPermissionsRequest)
    }

    try {
      val intent = Intent()
      val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
      if (pm.isIgnoringBatteryOptimizations(packageName)) {
        info("$packageName is ignoring battery optimizations already")
      } else {
        info("$packageName is NOT ignoring battery optimizations")
        intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
      }
    } catch (e: Exception) {
      warn("Caught error requesting battery permissions:", e)
    }

    Util.scheduleMmsSmsJob(applicationContext)
  }

    // TODO: The flow in this activity is all messed up. When the user signs in, we need to
  // request permissions and THEN start the SettingsActivity. As it stands, we hide the permissions
  // request because we start so early;
//  override fun onRequestPermissionsResult(requestCode: Int,
//                                          permissions: Array<String>, grantResults: IntArray) {
//    when (requestCode) {
//      myPermissionsRequest -> {
//        // If request is cancelled, the result arrays are empty.
//        if (grantResults.isNotEmpty()) {
//          for (grantResult in grantResults) {
//            if (grantResult == PackageManager.PERMISSION_DENIED) {
//              Toast.makeText(applicationContext, "Required permission not granted.", Toast.LENGTH_LONG).show()
//              break
//            }
//          }
//          startActivity(Intent(this, SettingsActivity::class.java))
//        }
//        return
//      }
//
//    // Add other 'when' lines to check for other
//    // permissions this app might request.
//      else -> {
//        // Ignore all other requests.
//      }
//    }
//  }
}
