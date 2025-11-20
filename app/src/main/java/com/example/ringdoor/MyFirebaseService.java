package com.example.ringdoor;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {

        String type = remoteMessage.getData().get("type");

        if ("doorbell".equals(type)) {

            // App đang mở → popup
            if (MainActivity.isForeground) {
                MainActivity.triggerDoorbellPopup();
                return;
            }

            // App đang tắt → tạo thông báo
            String title = remoteMessage.getData().get("title");
            String body  = remoteMessage.getData().get("body");

            showNotification(title, body);
        }
    }

    private void showNotification(String title, String body) {

        String channelId = "ringdoor_channel";
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("fromNotification", true);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT
        );

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channelId)
                        .setSmallIcon(R.drawable.ringdoor_logo)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setSound(sound)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(channelId, "RingDoor Alerts",
                            NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(channel);
        }

        int id = (int) System.currentTimeMillis();
        manager.notify(id, builder.build());
    }
}
