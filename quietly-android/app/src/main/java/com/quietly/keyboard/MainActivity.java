package com.quietly.keyboard;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_SETTINGS = "quietly_settings";
    private static final String KEY_API_KEY = "groq_api_key";
    private static final String KEY_MODEL = "groq_model";
    private static final int REQ_MIC_PERMISSION = 101;

    private final List<String> MODEL_NAMES = Arrays.asList(
            "⚡ Auto-Smart (Recommended: Fastest & Smartest)",
            "🚀 Ultra-Fast (llama-3.1-8b-instant)",
            "🌟 Flagship Quality (llama-3.3-70b-versatile)",
            "🧠 Deep Thinker (deepseek-r1-distill-llama-70b)",
            "🌐 Multilingual Specialist (deepseek-r1-distill-qwen-32b)"
    );

    private final List<String> MODEL_KEYS = Arrays.asList(
            "auto-smart",
            "llama-3.1-8b-instant",
            "llama-3.3-70b-versatile",
            "deepseek-r1-distill-llama-70b",
            "deepseek-r1-distill-qwen-32b"
    );

    private EditText etApiKey;
    private Spinner spModel;
    private TextView tvStatus;

    private TextView tvAccessibilityStatus;
    private TextView tvNotifStatus;
    private TextView tvMicStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etApiKey = findViewById(R.id.et_api_key);
        spModel = findViewById(R.id.sp_model);
        tvStatus = findViewById(R.id.tv_status);

        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        tvNotifStatus = findViewById(R.id.tv_notif_status);
        tvMicStatus = findViewById(R.id.tv_mic_status);

        // Populate Model dropdown with human-friendly names
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, MODEL_NAMES);
        spModel.setAdapter(adapter);

        // Load saved settings
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        String savedKey = prefs.getString(KEY_API_KEY, "");
        String savedModel = prefs.getString(KEY_MODEL, "auto-smart");

        etApiKey.setText(savedKey);
        int modelIdx = MODEL_KEYS.indexOf(savedModel);
        if (modelIdx < 0) modelIdx = 0; // Default to Auto-Smart
        spModel.setSelection(modelIdx);

        if (!savedKey.isEmpty()) {
            tvStatus.setText("✓ Groq API Key Saved (" + MODEL_NAMES.get(modelIdx) + ")");
        }

        // Save & Test Button
        Button btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> {
            String key = etApiKey.getText().toString().trim();
            int selectedPos = spModel.getSelectedItemPosition();
            String modelKey = (selectedPos >= 0 && selectedPos < MODEL_KEYS.size()) ? MODEL_KEYS.get(selectedPos) : "auto-smart";

            if (!key.startsWith("gsk_")) {
                Toast.makeText(this, "Key should start with gsk_...", Toast.LENGTH_SHORT).show();
            }

            prefs.edit()
                    .putString(KEY_API_KEY, key)
                    .putString(KEY_MODEL, modelKey)
                    .apply();

            tvStatus.setText("✓ Saved! Testing connection to Groq (" + modelKey + ")...");

            // Test connection
            GroqClient testClient = new GroqClient();
            testClient.generateReplies(key, modelKey, "Warm", "Hello there!", "", "en", new GroqClient.Callback() {
                @Override
                public void onSuccess(List<String> suggestions) {
                    tvStatus.setText("✓ Groq Connected! " + suggestions.size() + " suggestions ready.");
                    Toast.makeText(MainActivity.this, "Groq Connected Successfully!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    tvStatus.setText("⚠ Groq error: " + error);
                }
            });
        });

        // 1. Enable Keyboard in Settings
        Button btnEnable = findViewById(R.id.btn_enable);
        btnEnable.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(intent);
        });

        // 2. Select Keyboard as Active Input
        Button btnSelect = findViewById(R.id.btn_select);
        btnSelect.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        });

        // 3. Enable Live Chat Reader (Accessibility)
        Button btnAccessibility = findViewById(R.id.btn_enable_accessibility);
        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        // 4. Enable WhatsApp Notification Reader
        Button btnNotif = findViewById(R.id.btn_enable_notif);
        btnNotif.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        // 5. Enable Voice Typing Mic
        Button btnMic = findViewById(R.id.btn_enable_mic);
        btnMic.setOnClickListener(v -> {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC_PERMISSION);
        });

        // 6. Launch Clean YouTube
        Button btnLaunchYoutube = findViewById(R.id.btn_launch_youtube);
        btnLaunchYoutube.setOnClickListener(v -> {
            Intent intent = new Intent(this, YouTubeActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionBadges();
    }

    private void updatePermissionBadges() {
        // Accessibility Check
        boolean isAccessibilityEnabled = isAccessibilityServiceEnabled(this, QuietlyAccessibilityService.class);
        if (isAccessibilityEnabled) {
            tvAccessibilityStatus.setText("✓ Live Chat Reader is ACTIVE");
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.chip_sparkle));
        } else {
            tvAccessibilityStatus.setText("⚠ Disabled: Tap button above to enable in Accessibility");
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }

        // Notification Access Check
        boolean isNotifEnabled = isNotificationServiceEnabled(this);
        if (isNotifEnabled) {
            tvNotifStatus.setText("✓ Notification Reader is ACTIVE");
            tvNotifStatus.setTextColor(ContextCompat.getColor(this, R.color.chip_sparkle));
        } else {
            tvNotifStatus.setText("⚠ Disabled: Tap button above to allow Notification Access");
            tvNotifStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }

        // Microphone Permission Check
        boolean isMicGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        if (isMicGranted) {
            tvMicStatus.setText("✓ Voice Typing Microphone is GRANTED");
            tvMicStatus.setTextColor(ContextCompat.getColor(this, R.color.chip_sparkle));
        } else {
            tvMicStatus.setText("⚠ Not Granted: Tap button above to allow Mic for Voice Typing");
            tvMicStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }
    }

    private static boolean isAccessibilityServiceEnabled(Context context, Class<?> service) {
        ComponentName expected = new ComponentName(context, service);
        String enabledServices = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) return false;

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServices);
        while (colonSplitter.hasNext()) {
            String componentName = colonSplitter.next();
            ComponentName enabled = ComponentName.unflattenFromString(componentName);
            if (enabled != null && enabled.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNotificationServiceEnabled(Context context) {
        String pkg = context.getPackageName();
        String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(pkg);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_MIC_PERMISSION) {
            updatePermissionBadges();
        }
    }
}
