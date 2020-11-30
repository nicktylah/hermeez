package nicktylah.hermes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Listens for startup events, schedules initial sms/mms JobServices
 */
class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
      Util.scheduleMmsSmsJob(context)
    }
  }
}
