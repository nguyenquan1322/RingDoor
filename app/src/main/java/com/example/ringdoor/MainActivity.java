package com.example.ringdoor;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AlertDialog;

import com.google.firebase.database.*;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    LinearLayout btnUnlock, btnLock, btnLogout;
    TextView tvGreeting, tvDoorStatus;

    String deviceId = "esp32-frontdoor-01";

    DatabaseReference statusRef;
    DatabaseReference ringRef;

    ValueEventListener ringListener;

    public static boolean isForeground = false;
    public static MainActivity instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        instance = this;

        btnUnlock = findViewById(R.id.btnUnlock);
        btnLock = findViewById(R.id.btnLock);
        btnLogout = findViewById(R.id.btnLogout);
        tvGreeting = findViewById(R.id.tvGreeting);
        tvDoorStatus = findViewById(R.id.tvDoorStatus);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    101
            );
        }

        FirebaseMessaging.getInstance().subscribeToTopic("ringdoor")
                .addOnCompleteListener(task -> Log.d("FCM", "Subscribed"));

        SharedPreferences prefs = getSharedPreferences("RingDoorPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");
        String displayName = prefs.getString("displayName", "");

        if (username.isEmpty()) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        tvGreeting.setText(buildGreeting(displayName));

        // Listen door status
        statusRef = FirebaseDatabase.getInstance()
                .getReference("Devices")
                .child(deviceId)
                .child("status");

        statusRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                tvDoorStatus.setText(status != null ?
                        "📡 Trạng thái: " + status :
                        "📡 Không có phản hồi");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvDoorStatus.setText("⚠️ Lỗi đọc trạng thái!");
            }
        });

        btnUnlock.setOnClickListener(v -> {
            sendCommand("open_door", "");
            tvDoorStatus.setText("🔁 Đang mở cửa...");
        });

        btnLock.setOnClickListener(v -> {
            sendCommand("close_door", "");
            tvDoorStatus.setText("🔁 Đang đóng cửa...");
        });

        btnLogout.setOnClickListener(v -> {
            prefs.edit().clear().apply();
            Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        instance = this;

        // REGISTER BROADCAST RECEIVER
        IntentFilter filter = new IntentFilter("DOORBELL_EVENT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(doorbellReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(doorbellReceiver, filter);
        }

        // REGISTER FIREBASE LISTENER SAFE
        registerRingListener();

        if (getIntent().getBooleanExtra("fromNotification", false)) {
            showDoorbellPopup();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForeground = false;

        // GỠ RECEIVER
        unregisterReceiver(doorbellReceiver);

        // GỠ LISTENER FIREBASE
        if (ringRef != null && ringListener != null)
            ringRef.removeEventListener(ringListener);
    }

    // Firebase ring listener - SAFE
    private void registerRingListener() {

        ringRef = FirebaseDatabase.getInstance()
                .getReference("Devices")
                .child(deviceId)
                .child("statusRing");

        ringListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                if (!isForeground || isFinishing() || isDestroyed())
                    return;

                String state = snapshot.getValue(String.class);

                if ("ringOn".equals(state)) {
                    runOnUiThread(() -> showDoorbellPopup());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        ringRef.addValueEventListener(ringListener);
    }

    // Broadcast receiver
    private final BroadcastReceiver doorbellReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (!isForeground || isFinishing() || isDestroyed())
                return;

            showDoorbellPopup();
        }
    };

    private void sendCommand(String type, String value) {
        DatabaseReference cmd = FirebaseDatabase.getInstance()
                .getReference("Commands")
                .child(deviceId);

        String reqId = "req_" + System.currentTimeMillis();

        cmd.child("requestId").setValue(reqId);
        cmd.child("timestamp").setValue(ServerValue.TIMESTAMP);
        cmd.child("type").setValue(type);
        cmd.child("value").setValue(value);
    }

    private void showDoorbellPopup() {

        // FIX CRASH: activity đã đóng
        if (isFinishing() || isDestroyed()) {
            Log.d("POPUP", "Activity not running → skip popup");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        android.view.View view = inflater.inflate(R.layout.popup_doorbell, null, false);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        dialog.show();

        Button btnOpen = view.findViewById(R.id.btnOpenDoor);
        Button btnClose = view.findViewById(R.id.btnClosePopup);

        btnOpen.setOnClickListener(v -> {
            sendCommand("open_door", "");
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
    }

    private String buildGreeting(String name) {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        if (hour >= 5 && hour < 12)
            return "Chào buổi sáng, " + name + " ☀️";
        else if (hour >= 12 && hour < 18)
            return "Chào buổi chiều, " + name + " 🌤️";
        else
            return "Chào buổi tối, " + name + " 🌙";
    }
}
