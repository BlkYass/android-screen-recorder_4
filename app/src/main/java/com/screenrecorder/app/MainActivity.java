package com.screenrecorder.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private MediaProjectionManager projectionManager;
    private boolean isRecording = false;
    private Button btnRecord;
    private TextView tvStatus;

    private final ActivityResultLauncher<Intent> screenCaptureLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK
                        && result.getData() != null) {
                    startRecordingService(result.getResultCode(), result.getData());
                } else {
                    Toast.makeText(this,
                        "Screen capture permission denied",
                        Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnRecord = findViewById(R.id.btnRecord);
        tvStatus  = findViewById(R.id.tvStatus);

        projectionManager = (MediaProjectionManager)
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        btnRecord.setOnClickListener(v -> {
            if (!isRecording) {
                checkPermissionsAndStart();
            } else {
                stopRecording();
            }
        });
    }

    private void checkPermissionsAndStart() {
        // 1. Notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                return;
            }
        }
        // 2. Audio permission
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, 102);
            return;
        }
        // 3. Screen capture
        requestScreenCapture();
    }

    private void requestScreenCapture() {
        screenCaptureLauncher.launch(
            projectionManager.createScreenCaptureIntent());
    }

    private void startRecordingService(int resultCode, Intent data) {
        Intent serviceIntent = new Intent(this, RecorderService.class);
        serviceIntent.setAction("START");
        serviceIntent.putExtra("resultCode", resultCode);
        serviceIntent.putExtra("data", data);
        ContextCompat.startForegroundService(this, serviceIntent);

        isRecording = true;
        btnRecord.setText("⏹  Stop Recording");
        tvStatus.setText("● Recording…");
        tvStatus.setTextColor(
            ContextCompat.getColor(this, android.R.color.holo_red_light));
    }

    private void stopRecording() {
        Intent serviceIntent = new Intent(this, RecorderService.class);
        serviceIntent.setAction("STOP");
        startService(serviceIntent);

        isRecording = false;
        btnRecord.setText("⏺  Start Recording");
        tvStatus.setText("Ready");
        tvStatus.setTextColor(
            ContextCompat.getColor(this, android.R.color.darker_gray));

        Toast.makeText(this,
            "Saved to Movies/ScreenRecorder",
            Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkPermissionsAndStart();
        } else {
            Toast.makeText(this,
                "Permission required to record",
                Toast.LENGTH_SHORT).show();
        }
    }
}