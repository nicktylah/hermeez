package com.nicktylah.hermes;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Telephony;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Objects;

public class ForegroundMessageService extends Service {
  public static final String STOP_MESSAGE_FORWARDING = "STOP_MESSAGE_FORWARDING";

  private boolean running = false;

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    String TAG = "FregrndMessageService";
    if (intent == null) {
      Log.d(TAG, "Received null intent");
      return START_STICKY;
    }

    if (Objects.equals(intent.getAction(), STOP_MESSAGE_FORWARDING)) {
      Log.d(TAG, "stop forwarding called");
      stopSelf();
      running = false;
      return START_STICKY;
    }

    if (running) {
      Log.d(TAG, "service already running");
      return START_STICKY;
    }

    if (Objects.equals(intent.getAction(), MainActivity.MESSAGE_LISTEN)) {
      Log.d(TAG, "registering sms listener");
      SmsReceiver smsReceiver = new SmsReceiver(new Handler(), this);
      ContentResolver smsContentResolver = this.getContentResolver();
      smsContentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsReceiver);

      Log.d(TAG, "registering mms listener");
      MmsReceiver mmsReceiver = new MmsReceiver(new Handler(), this);
      ContentResolver mmsContentResolver = this.getContentResolver();
      mmsContentResolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, mmsReceiver);

      Log.d(TAG, "registering firebase listener");
      new FirebaseReceiver();

      Intent stopIntent = new Intent(this, ForegroundMessageService.class);
      stopIntent.setAction(STOP_MESSAGE_FORWARDING);
      PendingIntent pendingIntent = PendingIntent.getService(
          this,
          0,
          stopIntent,
          PendingIntent.FLAG_UPDATE_CURRENT);

      Notification notification = new Notification.Builder(this)
          .setContentTitle(getText(R.string.forwarding_notification_title))
          .setContentText(getText(R.string.forwarding_notification_message))
          .setSmallIcon(R.drawable.hermes_white)
          .setContentIntent(pendingIntent)
          .build();

      startForeground(123, notification);
      running = true;
    }

    // We want this service to continue running until it is explicitly
    // stopped, so return sticky.
    return START_STICKY;
  }
}
