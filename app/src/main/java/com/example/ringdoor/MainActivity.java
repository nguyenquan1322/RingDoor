package com.example.ringdoor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.*;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    LinearLayout btnUnlock, btnLock, btnLogout;
    TextView tvGreeting, tvDoorStatus;

    String deviceId = "esp32-frontdoor-01";

    DatabaseReference statusRef, commandTypeRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnUnlock = findViewById(R.id.btnUnlock);
        btnLock = findViewById(R.id.btnLock);
        btnLogout = findViewById(R.id.btnLogout);

        tvGreeting = findViewById(R.id.tvGreeting);
        tvDoorStatus = findViewById(R.id.tvDoorStatus);

        SharedPreferences prefs = getSharedPreferences("RingDoorPrefs", MODE_PRIVATE);
        String username = prefs.getString("username", "");
        String displayName = prefs.getString("displayName", "");

        if (username.isEmpty()) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        tvGreeting.setText(buildGreeting(displayName));

        // 📌 Theo dõi trạng thái cửa (giữ nguyên nếu vẫn dùng Devices để phản hồi)
        statusRef = FirebaseDatabase.getInstance()
                .getReference("Devices")
                .child(deviceId)
                .child("status");

        statusRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String status = snapshot.getValue(String.class);
                if (status != null) {
                    tvDoorStatus.setText("📡 Trạng thái: " + status);
                } else {
                    tvDoorStatus.setText("📡 Không có phản hồi");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                tvDoorStatus.setText("⚠️ Lỗi đọc trạng thái cửa!");
            }
        });

        // 🔔 Lắng nghe ESP32 gửi type = "doorbell"
        commandTypeRef = FirebaseDatabase.getInstance()
                .getReference("Commands")
                .child(deviceId)
                .child("type");

        commandTypeRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String type = snapshot.getValue(String.class);
                if (type == null) return;

                if (type.equals("doorbell")) {
                    showDoorbellPopup();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // 🔓 Mở cửa
        btnUnlock.setOnClickListener(v -> {
            sendCommand("open_door", "");
            tvDoorStatus.setText("🔁 Đang mở cửa...");
        });

        // 🔒 Đóng cửa
        btnLock.setOnClickListener(v -> {
            sendCommand("close_door", "");
            tvDoorStatus.setText("🔁 Đang đóng cửa...");
        });

        // 🚪 Đăng xuất
        btnLogout.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.clear();
            editor.apply();

            Toast.makeText(this, "Đã đăng xuất", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });
    }

    // ⭐ Gửi command vào node Commands/{deviceId}
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

    // ⭐ Khi có người bấm chuông
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

    // 🕐 Lời chào theo giờ
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
