package com.example.ringdoor;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.firebase.database.*;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    LinearLayout btnUnlock, btnLock, btnLogout;
    TextView tvGreeting, tvDoorStatus;

    String deviceId = "esp32-frontdoor-01";

    DatabaseReference statusRef;
    DatabaseReference ringRef;

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

        // 🔥 Android 13+ xin quyền nhận thông báo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    101
            );
        }

        // 🔥 Subsribe topic để nhận FCM
        FirebaseMessaging.getInstance().subscribeToTopic("ringdoor")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d("FCM", "Subscribed to ringdoor topic");
                    }
                });

        // ====== Check login ======
        SharedPreferences prefs = getSharedPreferences("RingDoorPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");
        String displayName = prefs.getString("displayName", "");

        if (username.isEmpty()) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        tvGreeting.setText(buildGreeting(displayName));

        // ====== Listen door status ======
        statusRef = FirebaseDatabase.getInstance()
                .getReference("Devices")
                .child(deviceId)
                .child("status");

        statusRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if (status != null)
                    tvDoorStatus.setText("📡 Trạng thái: " + status);
                else
                    tvDoorStatus.setText("📡 Không có phản hồi");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvDoorStatus.setText("⚠️ Lỗi đọc trạng thái cửa!");
            }
        });

        // ====== Listen doorbell ======
        ringRef = FirebaseDatabase.getInstance()
                .getReference("Devices")
                .child(deviceId)
                .child("statusRing");

        ringRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String state = snapshot.getValue(String.class);
                if ("ringOn".equals(state)) {

                    if (isForeground) {
                        showDoorbellPopup();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // ====== Buttons ======
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

        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("fromNotification", false)) {
            showDoorbellPopup();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForeground = false;
    }

    public static void triggerDoorbellPopup() {
        if (instance != null && isForeground) {
            instance.showDoorbellPopup();
        }
    }

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
        new android.app.AlertDialog.Builder(this)
                .setTitle("🔔 Chuông cửa")
                .setMessage("Có người bấm chuông! Bạn có muốn mở cửa không?")
                .setPositiveButton("Mở cửa", (d, w) ->
                        sendCommand("open_door", "")
                )
                .setNegativeButton("Đóng", null)
                .show();
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
