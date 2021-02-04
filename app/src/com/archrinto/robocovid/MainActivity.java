package com.archrinto.robocovid;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private TextView mInputIpRobot;
    private boolean permissionGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mInputIpRobot = (TextView) findViewById(R.id.inputIPRobot);
    }

    public void startRemote(View view) {
        if (permissionGranted) {
            String robotIp = mInputIpRobot.getText().toString().trim();
            Intent intent = new Intent(this, RemoteActivity.class);
            intent.putExtra("robot_ip", robotIp);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Aplikasi tidak bisa berjalan tanpa izin", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true;
        } else {
           requestPermissions(new String[] { Manifest.permission.INTERNET, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 200);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 200) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Aplikasi tidak bisa berjalan tanpa jaringan", Toast.LENGTH_SHORT).show();
                finish();
            } else if (grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Aplikasi tidak bisa berjalan tanpa camera", Toast.LENGTH_SHORT).show();
                finish();
            } else if (grantResults[2] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Aplikasi tidak bisa berjalan tanpa audio", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                permissionGranted = true;
            }
        }
    }
}