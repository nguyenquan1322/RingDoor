package com.example.ringdoor;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.*;
import java.util.HashMap;

public class RegisterActivity extends AppCompatActivity {

    private EditText etDisplayName, etNewUser, etNewPass;
    private Button btnRegister, btnBack;
    private DatabaseReference userRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        userRef = FirebaseDatabase.getInstance().getReference("Users");

        etDisplayName = findViewById(R.id.etDisplayName);
        etNewUser = findViewById(R.id.etNewUser);
        etNewPass = findViewById(R.id.etNewPass);
        btnRegister = findViewById(R.id.btnRegister);
        btnBack = findViewById(R.id.btnBack);

        btnRegister.setOnClickListener(v -> registerUser());
        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String displayName = etDisplayName.getText().toString().trim();
        String username = etNewUser.getText().toString().trim();
        String password = etNewPass.getText().toString().trim();

        if (TextUtils.isEmpty(displayName) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 4) {
            Toast.makeText(this, "Mật khẩu phải ít nhất 4 ký tự", Toast.LENGTH_SHORT).show();
            return;
        }

        userRef.child(username).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(RegisterActivity.this, "Tên người dùng đã tồn tại!", Toast.LENGTH_SHORT).show();
                } else {
                    HashMap<String, Object> userMap = new HashMap<>();
                    userMap.put("displayName", displayName);
                    userMap.put("username", username);
                    userMap.put("password", password);

                    userRef.child(username).setValue(userMap).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(RegisterActivity.this, "🎉 Đăng ký thành công! Vui lòng đăng nhập", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                            finish();
                        } else {
                            Toast.makeText(RegisterActivity.this, "Lỗi khi lưu dữ liệu!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(RegisterActivity.this, "Lỗi kết nối Firebase!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
