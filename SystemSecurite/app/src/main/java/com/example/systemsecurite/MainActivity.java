package com.example.systemsecurite;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION_BT = 1;
    private static final int REQUEST_PERMISSION_NOTIF = 2;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final long PONG_TIMEOUT_MS = 20000;

    // Notifications de mouvement
    private static final String CHANNEL_ID = "mouvement_channel";
    private static final int NOTIF_ID_MOTION = 1001;

    private BluetoothAdapter bluetoothAdapter;
    private static BluetoothSocket bluetoothSocket;
    private static OutputStream outputStream;
    private static InputStream inputStream;
    private static volatile boolean isConnected = false;
    private static boolean isThreadRunning = false;
    private static String connectedDeviceName = "";
    private static BluetoothDevice lastDevice = null;
    private static boolean isManualDisconnect = false;
    private static boolean isConnecting = false;

    private Button btnBluetooth;
    private MaterialButton btnThemeToggle;
    private MaterialButton btnHistorique;
    private TextView tvStatus, tvSignalCour, tvSignalToilette, tvLdrValue;
    private MaterialButton btnSalon, btnCour, btnToilette;
    private LinearProgressIndicator progressLdr;
    private MaterialCardView cardSalon, cardCour, cardToilette;
    private MaterialCardView badgeSignalCour, badgeSignalToilette;
    private ImageView imgSalonBulb, imgCourBulb, imgToiletteBulb;
    private ImageView imgPirCour, imgPirToilette;
    private View viewSignalCour, viewSignalToilette;

    private boolean isSalonOn = false;
    private boolean isCourManualOn = false;
    private boolean isToiletteManualOn = false;
    private boolean isToiletteBlinking = false;
    private boolean isCourBlinking = false;
    private static volatile long lastPongTime = 0;

    private SharedPreferences themePrefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable toiletteBlinkRunnable = new Runnable() {
        private boolean visible = true;
        @Override
        public void run() {
            if (!isToiletteBlinking) return;
            imgPirToilette.setColorFilter(visible ? Color.parseColor("#FBBC04") : Color.GRAY);
            visible = !visible;
            mainHandler.postDelayed(this, 500);
        }
    };

    private final Runnable courBlinkRunnable = new Runnable() {
        private boolean visible = true;
        @Override
        public void run() {
            if (!isCourBlinking) return;
            imgPirCour.setColorFilter(visible ? Color.RED : Color.GRAY);
            visible = !visible;
            mainHandler.postDelayed(this, 500);
        }
    };

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (isConnected) {
                if (System.currentTimeMillis() - lastPongTime > PONG_TIMEOUT_MS) {
                    handleConnectionLoss();
                } else {
                    sendCommand("PING\n");
                    mainHandler.postDelayed(() -> { if(isConnected) sendCommand("LDR\n"); }, 3000);
                }
            }
            mainHandler.postDelayed(this, 8000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        themePrefs = getSharedPreferences("ThemePrefs", MODE_PRIVATE);
        if (themePrefs.getBoolean("isDarkMode", false)) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);

        initViews();
        createNotificationChannel();
        requestNotificationPermissionIfNeeded();

        if (isConnected && bluetoothSocket != null && bluetoothSocket.isConnected()) {
            updateUIConnected();
            startReceiving();
            sendCommand("ETAT\n");
        } else if (lastDevice != null && !isManualDisconnect) attemptAutoReconnect();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        btnBluetooth.setOnClickListener(v -> {
            if (isConnected) { isManualDisconnect = true; disconnectBluetooth(); }
            else { isManualDisconnect = false; checkPermissionsAndConnect(); }
        });

        btnThemeToggle.setOnClickListener(v -> {
            boolean isDark = themePrefs.getBoolean("isDarkMode", false);
            themePrefs.edit().putBoolean("isDarkMode", !isDark).apply();
            AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES);
            recreate();
        });

        btnHistorique.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, HistoriqueActivity.class)));

        btnSalon.setOnClickListener(v -> { if (isConnected) sendCommand(isSalonOn ? "SALON_OFF\n" : "SALON_ON\n"); });
        btnCour.setOnClickListener(v -> { if (isConnected) sendCommand(isCourManualOn ? "COUR_OFF\n" : "COUR_ON\n"); });
        btnToilette.setOnClickListener(v -> { if (isConnected) sendCommand(isToiletteManualOn ? "TOILETTE_OFF\n" : "TOILETTE_ON\n"); });

        mainHandler.removeCallbacks(heartbeatRunnable);
        mainHandler.postDelayed(heartbeatRunnable, 5000);
    }

    private void initViews() {
        btnBluetooth = findViewById(R.id.btnBluetooth);
        tvStatus = findViewById(R.id.tvStatus);
        tvSignalCour = findViewById(R.id.tvSignalCour);
        tvSignalToilette = findViewById(R.id.tvSignalToilette);
        tvLdrValue = findViewById(R.id.tvLdrValue);
        progressLdr = findViewById(R.id.progressLdr);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        btnHistorique = findViewById(R.id.btnHistorique);
        btnSalon = findViewById(R.id.btnSalon);
        btnCour = findViewById(R.id.btnCour);
        btnToilette = findViewById(R.id.btnToilette);
        cardSalon = findViewById(R.id.cardSalon);
        cardCour = findViewById(R.id.cardCour);
        cardToilette = findViewById(R.id.cardToilette);
        imgPirCour = findViewById(R.id.imgPirCour);
        imgPirToilette = findViewById(R.id.imgPirToilette);
        imgSalonBulb = findViewById(R.id.imgSalonBulb);
        imgCourBulb = findViewById(R.id.imgCourBulb);
        imgToiletteBulb = findViewById(R.id.imgToiletteBulb);
        viewSignalCour = findViewById(R.id.viewSignalCour);
        viewSignalToilette = findViewById(R.id.viewSignalToilette);
        badgeSignalCour = findViewById(R.id.badgeSignalCour);
        badgeSignalToilette = findViewById(R.id.badgeSignalToilette);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Détection de mouvement",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications de mouvement détecté à La Cour");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_PERMISSION_NOTIF);
        }
    }

    private void showMotionNotification(String dateTimeStr) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return; // pas de permission -> l'événement reste quand même dans l'historique
        }

        Intent intent = new Intent(this, HistoriqueActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Mouvement détecté - La Cour")
                .setContentText("Mouvement " + dateTimeStr)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(NOTIF_ID_MOTION, builder.build());
    }


    private void updateUIConnected() {
        runOnUiThread(() -> {
            tvStatus.setText(String.format("Connecté : %s", connectedDeviceName));
            tvStatus.setTextColor(Color.parseColor("#4CAF50"));
            btnBluetooth.setText("Déconnecter");
        });
    }

    private void handleConnectionLoss() {
        if (isConnected) {
            isConnected = false;
            runOnUiThread(() -> { tvStatus.setText("Lien perdu, reconnexion..."); tvStatus.setTextColor(Color.RED); });
            attemptAutoReconnect();
        }
    }

    private void checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQUEST_PERMISSION_BT);
                return;
            }
        }
        showDeviceList();
    }

    private void showDeviceList() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return;
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> names = new ArrayList<>();
        ArrayList<BluetoothDevice> devices = new ArrayList<>();
        for (BluetoothDevice d : pairedDevices) { names.add(d.getName() + "\n" + d.getAddress()); devices.add(d); }
        new AlertDialog.Builder(this).setTitle("Bluetooth").setItems(names.toArray(new CharSequence[0]), (d, w) -> connectToDevice(devices.get(w))).show();
    }

    private synchronized void connectToDevice(BluetoothDevice device) {
        if (isConnecting) return;
        isConnecting = true;
        lastDevice = device;
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return;
                if (bluetoothSocket != null) try { bluetoothSocket.close(); } catch (Exception ignored){}
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();
                isConnected = true;
                connectedDeviceName = device.getName();
                lastPongTime = System.currentTimeMillis();
                updateUIConnected();
                startReceiving();
                sendCommand("ETAT\n");
            } catch (IOException e) {
                if (!isManualDisconnect) mainHandler.postDelayed(this::attemptAutoReconnect, 4000);
            } finally { isConnecting = false; }
        }).start();
    }

    private void attemptAutoReconnect() { if (lastDevice != null && !isConnected && !isManualDisconnect) connectToDevice(lastDevice); }

    private void startReceiving() {
        if (isThreadRunning) return;
        isThreadRunning = true;
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            StringBuilder sb = new StringBuilder();
            while (isConnected) {
                try {
                    int b = inputStream.read(buffer);
                    if (b == -1) { handleConnectionLoss(); break; }
                    sb.append(new String(buffer, 0, b));
                    int nlIndex;
                    while ((nlIndex = sb.indexOf("\n")) != -1) {
                        String msg = sb.substring(0, nlIndex).trim();
                        sb.delete(0, nlIndex + 1);
                        runOnUiThread(() -> processMessage(msg));
                    }
                } catch (IOException e) { handleConnectionLoss(); break; }
            }
            isThreadRunning = false;
        }).start();
    }

    private void processMessage(String msg) {
        if (msg.equals("SALON_ON")) updateSalonUI(true);
        else if (msg.equals("SALON_OFF")) updateSalonUI(false);
        else if (msg.startsWith("COUR_ROUGE")) {
            // Le PIC envoie maintenant "COUR_ROUGE DD/MM/YYYY HH:MM:SS"
            String dateTimeStr = msg.length() > "COUR_ROUGE".length()
                    ? msg.substring("COUR_ROUGE".length()).trim()
                    : "";
            startCourBlink();
            updateCourUI_Display(true, Color.RED, "MOUVEMENT");
            if (!dateTimeStr.isEmpty()) {
                HistoryManager.addEntry(this, "Mouvement", dateTimeStr);
                showMotionNotification(dateTimeStr);
            }
        }
        else if (msg.equals("COUR_JAUNE")) { stopCourBlink(); isCourManualOn = true; updateCourUI_Display(true, Color.parseColor("#FBBC04"), "MANUEL"); }
        else if (msg.equals("COUR_OFF")) { stopCourBlink(); isCourManualOn = false; updateCourUI_Display(false, Color.GRAY, "SIGNAL RAS"); }
        else if (msg.equals("TOILETTE_DETECTION")) { updateToiletteUI_Display(true, Color.parseColor("#FBBC04"), "OCCUPÉ", true); }
        else if (msg.equals("TOILETTE_JAUNE")) { isToiletteManualOn = true; updateToiletteUI_Display(true, Color.parseColor("#FBBC04"), "OCCUPÉ", false); }
        else if (msg.equals("TOILETTE_OFF")) { isToiletteManualOn = false; updateToiletteUI_Display(false, Color.GRAY, "LIBRE", false); }
        else if (msg.startsWith("LDR=")) {
            String val = msg.substring(4);
            tvLdrValue.setText(val);
            try { progressLdr.setProgress(Integer.parseInt(val)); } catch(Exception ignored){}
        }
        else if (msg.equals("PONG")) lastPongTime = System.currentTimeMillis();
    }

    private void updateSalonUI(boolean on) {
        isSalonOn = on;
        int color = on ? getResources().getColor(R.color.colorAccent) : getResources().getColor(R.color.icon_inactive);
        imgSalonBulb.setColorFilter(color);
        btnSalon.setIconTint(ColorStateList.valueOf(color));
        cardSalon.setCardBackgroundColor(on ? getResources().getColor(R.color.salon_on_bg) : getResources().getColor(R.color.card_surface));
    }

    private void updateCourUI_Display(boolean on, int color, String text) {
        imgCourBulb.setColorFilter(on ? color : getResources().getColor(R.color.icon_inactive));
        btnCour.setIconTint(ColorStateList.valueOf(isCourManualOn ? Color.parseColor("#FBBC04") : getResources().getColor(R.color.icon_inactive)));
        updatePIRSignal(badgeSignalCour, viewSignalCour, tvSignalCour, text, color);
        cardCour.setCardBackgroundColor(on ? getResources().getColor(color == Color.RED ? R.color.cour_motion_bg : R.color.cour_manual_bg) : getResources().getColor(R.color.card_surface));
    }

    private void updateToiletteUI_Display(boolean on, int color, String text, boolean blink) {
        imgToiletteBulb.setColorFilter(on ? color : getResources().getColor(R.color.icon_inactive));
        btnToilette.setIconTint(ColorStateList.valueOf(isToiletteManualOn ? Color.parseColor("#FBBC04") : getResources().getColor(R.color.icon_inactive)));
        updatePIRSignal(badgeSignalToilette, viewSignalToilette, tvSignalToilette, text, color);
        cardToilette.setCardBackgroundColor(on ? getResources().getColor(R.color.toilette_on_bg) : getResources().getColor(R.color.card_surface));
        if (blink) { isToiletteBlinking = true; mainHandler.post(toiletteBlinkRunnable); }
        else { isToiletteBlinking = false; mainHandler.removeCallbacks(toiletteBlinkRunnable); }
    }

    private void startCourBlink() { if (!isCourBlinking) { isCourBlinking = true; mainHandler.post(courBlinkRunnable); } }
    private void stopCourBlink() { isCourBlinking = false; mainHandler.removeCallbacks(courBlinkRunnable); imgPirCour.setColorFilter(Color.GRAY); }

    private void updatePIRSignal(MaterialCardView badge, View dot, TextView tv, String text, int color) {
        dot.setBackgroundTintList(ColorStateList.valueOf(color));
        tv.setText(text);
        int alphaColor = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color));
        badge.setCardBackgroundColor(alphaColor);
    }

    private void sendCommand(String c) {
        if (outputStream != null) new Thread(() -> { try { outputStream.write(c.getBytes()); } catch (Exception e) { runOnUiThread(this::handleConnectionLoss); } }).start();
    }

    private void disconnectBluetooth() {
        isConnected = false; isThreadRunning = false;
        try { if(inputStream!=null)inputStream.close(); if(outputStream!=null)outputStream.close(); if(bluetoothSocket!=null)bluetoothSocket.close(); } catch(Exception ignored){}
        runOnUiThread(() -> { tvStatus.setText("Non connecté"); tvStatus.setTextColor(Color.GRAY); btnBluetooth.setText("Connecter"); resetUI(); });
    }

    private void resetUI() {
        updateSalonUI(false); isCourManualOn = false; isToiletteManualOn = false;
        updateCourUI_Display(false, Color.GRAY, "SIGNAL RAS"); updateToiletteUI_Display(false, Color.GRAY, "LIBRE", false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(heartbeatRunnable);
        if (!isChangingConfigurations()) disconnectBluetooth();
    }
}