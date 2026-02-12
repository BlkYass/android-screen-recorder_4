package com.screenrecorder.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RecorderService extends Service {

    private static final String CHANNEL_ID  = "ScreenRecorderChannel";
    private static final int    NOTIF_ID    = 1;

    private MediaProjection mediaProjection;
    private MediaRecorder   mediaRecorder;
    private VirtualDisplay  virtualDisplay;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if ("START".equals(action)) {
            int    resultCode = intent.getIntExtra("resultCode", -1);
            Intent data       = intent.getParcelableExtra("data");
            createNotificationChannel();
            startForeground(NOTIF_ID, buildNotification());
            startRecording(resultCode, data);

        } else if ("STOP".equals(action)) {
            stopRecording();
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    // ─── Recording ────────────────────────────────────────────────────────────

    private void startRecording(int resultCode, Intent data) {
        MediaProjectionManager mgr =
            (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mgr.getMediaProjection(resultCode, data);

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getMetrics(metrics);
        int width  = metrics.widthPixels;
        int height = metrics.heightPixels;
        int dpi    = metrics.densityDpi;

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoSize(width, height);
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
        mediaRecorder.setAudioEncodingBitRate(128000);
        mediaRecorder.setAudioSamplingRate(44100);

        String timestamp = new SimpleDateFormat(
            "yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "REC_" + timestamp + ".mp4";

        setOutputFile(fileName);

        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenRecorder",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.getSurface(),
            null, null
        );

        mediaRecorder.start();
    }

    private void setOutputFile(String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ — use MediaStore (no storage permission needed)
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
            cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            cv.put(MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/ScreenRecorder");

            Uri uri = getContentResolver().insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);

            if (uri != null) {
                try {
                    FileDescriptor fd = getContentResolver()
                        .openFileDescriptor(uri, "w")
                        .getFileDescriptor();
                    mediaRecorder.setOutputFile(fd);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            // Android 7–9 — write directly to filesystem
            File dir = new File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES), "ScreenRecorder");
            if (!dir.exists()) dir.mkdirs();
            mediaRecorder.setOutputFile(new File(dir, fileName).getAbsolutePath());
        }
    }

    private void stopRecording() {
        try {
            if (mediaRecorder != null) {
                mediaRecorder.stop();
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception e) { e.printStackTrace(); }

        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
        } catch (Exception e) { e.printStackTrace(); }

        try {
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "Screen Recorder",
            NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Recording in progress");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, RecorderService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recorder")
            .setContentText("Recording in progress…")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPending)
            .setOngoing(true)
            .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}