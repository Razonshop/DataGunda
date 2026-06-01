package com.bhai.datagunda;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    TextView tvTotal, tvSpeed, tvHotspot;
    Button btnPermission;

    long lastTotalBytes = 0;
    double sessionStartMB = 0;
    double BURST_LIMIT = 300; // MB

    Handler handler = new Handler();
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTotal = findViewById(R.id.tvTotal);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvHotspot = findViewById(R.id.tvHotspot);
        btnPermission = findViewById(R.id.btnPermission);

        prefs = getSharedPreferences("DataUsage", MODE_PRIVATE);

        btnPermission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            }
        });

        if (!hasUsagePermission()) {
            Toast.makeText(this, "Please grant Usage Access permission", Toast.LENGTH_LONG).show();
        } else {
            btnPermission.setVisibility(View.GONE);
            startTracking();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasUsagePermission()) {
            btnPermission.setVisibility(View.GONE);
            startTracking();
        }
    }

    private boolean hasUsagePermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            getPackageName()
        );
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void startTracking() {
        lastTotalBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
        sessionStartMB = getTodayDataMB();
        handler.removeCallbacks(updateRunnable);
        handler.post(updateRunnable);
    }

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateData();
            handler.postDelayed(this, 1000);
        }
    };

    private void updateData() {
        long currentTotalBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
        long mobileBytes = TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes();

        double speedMBs = (currentTotalBytes - lastTotalBytes) / 1024.0 / 1024.0;
        lastTotalBytes = currentTotalBytes;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        double todayMB = prefs.getFloat(today, 0);
        todayMB += speedMBs;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat(today, (float) todayMB);
        editor.apply();

        // 300MB burst check
        if (todayMB - sessionStartMB > BURST_LIMIT) {
            Toast.makeText(MainActivity.this, "ALERT: 300MB used at once!", Toast.LENGTH_LONG).show();
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(500);
            sessionStartMB = todayMB;
        }

        // Update UI
        tvTotal.setText("Today Total: " + String.format(Locale.getDefault(), "%.1f MB", todayMB));
        tvSpeed.setText("Speed: " + String.format(Locale.getDefault(), "%.2f MB/s", speedMBs));
        tvHotspot.setText("Hotspot Share: " + (mobileBytes / 1024 / 1024) + " MB");

        // Red warning when 90% of 1500MB limit reached
        if (todayMB > 1350) {
            tvTotal.setTextColor(0xFFFF0000);
        } else {
            tvTotal.setTextColor(0xFF00FF00);
        }
    }

    private double getTodayDataMB() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return prefs.getFloat(today, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateRunnable);
    }
}
