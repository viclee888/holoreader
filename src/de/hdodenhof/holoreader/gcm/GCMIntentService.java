package de.hdodenhof.holoreader.gcm;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.gson.Gson;

import de.hdodenhof.holoreader.R;
import de.hdodenhof.holoreader.activities.HomeActivity;
import de.hdodenhof.holoreader.misc.Extras;
import de.hdodenhof.holoreader.misc.Prefs;
import de.hdodenhof.holoreader.provider.RSSContentProvider;
import de.hdodenhof.holoreader.provider.SQLiteHelper.FeedDAO;
import de.hdodenhof.holoreader.services.RefreshFeedService;

public class GCMIntentService extends IntentService {

    @SuppressWarnings("unused")
    private static final String TAG = GCMIntentService.class.getName();

    private static final String MESSAGETYPE_ADDFEED = "addfeed";

    public GCMIntentService() {
        super("GCMIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        String gcmMessageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {
            if (GoogleCloudMessaging.MESSAGE_TYPE_SEND_ERROR.equals(gcmMessageType)) {
                Log.v(TAG, "Error: " + extras.toString());
            } else if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(gcmMessageType)) {
                Log.v(TAG, "Message received");

                String messageType = intent.getStringExtra("type");
                if (messageType.equals(MESSAGETYPE_ADDFEED)) {
                    Log.v(TAG, "... handling addFeed message");
                    handleAddFeedMessage(intent.getStringExtra("data"));
                }
                Log.i(TAG, "Received: " + extras.toString());
            }
        }
        GCMReceiver.completeWakefulIntent(intent);
    }

    @SuppressLint("InlinedApi")
    private void handleAddFeedMessage(String data) {
        VOFeed[] feeds = new Gson().fromJson(data, VOFeed[].class);

        ContentResolver contentResolver = getContentResolver();

        for (VOFeed voFeed : feeds) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(FeedDAO.NAME, voFeed.getTitle());
            contentValues.put(FeedDAO.URL, voFeed.getUrl());

            Uri newFeed = contentResolver.insert(RSSContentProvider.URI_FEEDS, contentValues);
            int feedId = Integer.parseInt(newFeed.getLastPathSegment());

            Intent intent = new Intent(this, RefreshFeedService.class);
            intent.putExtra(Extras.FEEDID, feedId);

            startService(intent);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int newFeedsSoFar = prefs.getInt(Prefs.NEW_FEEDS, 0);
        int newFeedsSum = newFeedsSoFar + feeds.length;
        prefs.edit().putInt(Prefs.NEW_FEEDS, newFeedsSum).commit();

        Intent notificationIntent = new Intent(this, HomeActivity.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this);
        nb.setContentTitle(getString(R.string.FeedsAddedViaPush));
        nb.setContentText(getResources().getQuantityString(R.plurals.numberOfFeedsReceived, newFeedsSum, newFeedsSum));
        nb.setSmallIcon(R.drawable.notification);
        nb.setContentIntent(contentIntent);

        // TODO use updated API
        @SuppressWarnings("deprecation")
        Notification notification = nb.getNotification();
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0x1, notification);
    }

}
