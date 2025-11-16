package com.example.ringdoor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.util.HashMap;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private DatabaseReference userRef, deviceRef;

    // ⚙️ ID thiết bị của phần cứng ESP32
    private static final String DEVICE_ID = "esp32-frontdoor-01";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);

        userRef = FirebaseDatabase.getInstance().getReference("Users");
        deviceRef = FirebaseDatabase.getInstance().getReference("Devices").child(DEVICE_ID);

        // ✅ Auto-login nếu đã đăng nhập (ĐÃ SỬA SANG RINGDOOR)
        SharedPreferences prefs = getSharedPreferences("RingDoorPrefs", MODE_PRIVATE);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);

        if (isLoggedIn) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }

        btnLogin.setOnClickListener(v -> loginUser());
        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });
    }

    private void loginUser() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tài khoản và mật khẩu!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 🧠 Tài khoản mặc định (GIỮ NGUYÊN)
        if (username.equals("admin") && password.equals("12345")) {
            saveLogin(username, "Admin");
            assignDeviceToUser(username);
            Toast.makeText(this, "Đăng nhập thành công ✅", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
            return;
        }

        // 🔍 Kiểm tra Firebase (GIỮ NGUYÊN)
        userRef.child(username).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String storedPass = snapshot.child("password").getValue(String.class);
                    String displayName = snapshot.child("displayName").getValue(String.class);

                    if (storedPass != null && storedPass.equals(password)) {
                        saveLogin(username, displayName);
                        assignDeviceToUser(username);
                        Toast.makeText(LoginActivity.this, "Đăng nhập thành công ✅", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Sai mật khẩu ❌", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(LoginActivity.this, "Tài khoản không tồn tại ❌", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(LoginActivity.this, "Lỗi kết nối Firebase!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 💾 Lưu thông tin đăng nhập cục bộ (ĐÃ SỬA SANG RINGDOOR)
    private void saveLogin(String username, String displayName) {
        SharedPreferences prefs = getSharedPreferences("RingDoorPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("username", username);
        editor.putString("displayName", displayName);
        editor.putBoolean("isLoggedIn", true);
        editor.apply();
    }

    // 🔗 Gắn thiết bị phần cứng với tài khoản đang đăng nhập (GIỮ NGUYÊN)
    private void assignDeviceToUser(String username) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("assignedUser", username);
        map.put("assignedAt", ServerValue.TIMESTAMP);
        deviceRef.updateChildren(map);
    }
}
