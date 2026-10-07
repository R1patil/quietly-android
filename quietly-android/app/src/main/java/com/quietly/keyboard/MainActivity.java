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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS_SETTINGS = "quietly_settings";
    public static final String KEY_API_KEY = "groq_api_key";
    public static final String KEY_MODEL = "groq_model";
    public static final String KEY_ELIGIBLE_MODELS = "groq_eligible_models";
    private static final int REQ_MIC_PERMISSION = 101;

    private final List<String> modelDisplayNames = new ArrayList<>();
    private final List<String> modelKeys = new ArrayList<>();
    private ArrayAdapter<String> modelAdapter;
    private GroqClient groqClient;

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

        groqClient = new GroqClient();

        etApiKey = findViewById(R.id.et_api_key);
        spModel = findViewById(R.id.sp_model);
        tvStatus = findViewById(R.id.tv_status);

        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        tvNotifStatus = findViewById(R.id.tv_notif_status);
        tvMicStatus = findViewById(R.id.tv_mic_status);

        // Setup Model Spinner adapter
        modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modelDisplayNames);
        spModel.setAdapter(modelAdapter);

        // Load saved settings
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        String savedKey = prefs.getString(KEY_API_KEY, "");
        String savedModel = prefs.getString(KEY_MODEL, "auto-smart");
        String cachedModelsRaw = prefs.getString(KEY_ELIGIBLE_MODELS, "");

        List<String> cachedList = parseModelsList(cachedModelsRaw);
        if (!cachedList.isEmpty()) {
            GroqClient.setCachedEligibleModels(cachedList);
            updateModelSpinner(cachedList, savedModel);
        } else {
            updateModelSpinner(null, "auto-smart");
        }

        etApiKey.setText(savedKey);

        if (!savedKey.isEmpty()) {
            tvStatus.setText("✓ Groq API Key Saved (" + savedModel + ")");
            // Check eligibility in background to refresh models list
            checkModelEligibility(savedKey, false, null);
        }

        // 🔍 Check Eligibility Button
        Button btnCheckModels = findViewById(R.id.btn_check_models);
        if (btnCheckModels != null) {
            btnCheckModels.setOnClickListener(v -> {
                String key = etApiKey.getText().toString().trim();
                if (key.isEmpty()) {
                    Toast.makeText(this, "Please enter your Groq API key first", Toast.LENGTH_SHORT).show();
                    return;
                }
                checkModelEligibility(key, true, null);
            });
        }

        // Save & Test Button
        Button btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> {
            String key = etApiKey.getText().toString().trim();
            if (key.isEmpty()) {
                Toast.makeText(this, "Please enter your Groq API key", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!key.startsWith("gsk_")) {
                Toast.makeText(this, "Key should start with gsk_...", Toast.LENGTH_SHORT).show();
            }

            int selectedPos = spModel.getSelectedItemPosition();
            String chosenModelKey = (selectedPos >= 0 && selectedPos < modelKeys.size())
                    ? modelKeys.get(selectedPos) : "auto-smart";

            // Verify eligibility first, then test and save
            tvStatus.setText("⏳ Checking model eligibility with Groq API...");
            checkModelEligibility(key, false, () -> {
                int posAfter = spModel.getSelectedItemPosition();
                String finalModelKey = (posAfter >= 0 && posAfter < modelKeys.size())
                        ? modelKeys.get(posAfter) : chosenModelKey;

                prefs.edit()
                        .putString(KEY_API_KEY, key)
                        .putString(KEY_MODEL, finalModelKey)
                        .apply();

                String resolvedTestModel = GroqClient.resolveModel(finalModelKey, "Warm", false);
                tvStatus.setText("⏳ Testing connection with eligible model (" + resolvedTestModel + ")...");

                groqClient.generateReplies(key, finalModelKey, "Warm", "Hello there!", "", "en", new GroqClient.Callback() {
                    @Override
                    public void onSuccess(List<String> suggestions) {
                        tvStatus.setText("✓ Groq Connected! Model `" + resolvedTestModel + "` verified (" + suggestions.size() + " suggestions ready).");
                        Toast.makeText(MainActivity.this, "Groq Connected Successfully with " + resolvedTestModel + "!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String error) {
                        tvStatus.setText("⚠ Groq error: " + error);
                    }
                });
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

    private List<String> parseModelsList(String raw) {
        List<String> list = new ArrayList<>();
        if (raw != null && !raw.trim().isEmpty()) {
            String[] parts = raw.split(",");
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                    list.add(trimmed);
                }
            }
        }
        return list;
    }

    private void updateModelSpinner(List<String> eligibleModels, String selectedKey) {
        modelDisplayNames.clear();
        modelKeys.clear();

        // 1. Auto-Smart option always at index 0
        modelDisplayNames.add("⚡ Auto-Smart (Recommended: Fastest & Smartest)");
        modelKeys.add("auto-smart");

        if (eligibleModels != null) {
            for (String modelId : eligibleModels) {
                if ("auto-smart".equalsIgnoreCase(modelId)) continue;
                modelDisplayNames.add(formatModelLabel(modelId));
                modelKeys.add(modelId);
            }
        }

        modelAdapter.notifyDataSetChanged();

        // Restore selected key
        int selIdx = 0;
        if (selectedKey != null && !selectedKey.isEmpty()) {
            int found = modelKeys.indexOf(selectedKey);
            if (found >= 0) selIdx = found;
        }
        spModel.setSelection(selIdx);
    }

    private String formatModelLabel(String modelId) {
        String mLower = modelId.toLowerCase();
        if (mLower.contains("120b") || mLower.contains("70b") || mLower.contains("versatile")) {
            return "🌟 " + modelId + " (Flagship Quality)";
        } else if (mLower.contains("qwen") || mLower.contains("allam")) {
            return "🌐 " + modelId + " (Multilingual)";
        } else if (mLower.contains("8b") || mLower.contains("20b") || mLower.contains("mini") || mLower.contains("instant")) {
            return "🚀 " + modelId + " (Ultra-Fast)";
        } else if (mLower.contains("reason") || mLower.contains("think") || mLower.contains("compound")) {
            return "🧠 " + modelId + " (Reasoning)";
        }
        return "🤖 " + modelId;
    }

    private void checkModelEligibility(String apiKey, boolean showToast, Runnable onComplete) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (showToast) Toast.makeText(this, "Please enter your Groq API key first", Toast.LENGTH_SHORT).show();
            return;
        }

        tvStatus.setText("⏳ Checking model eligibility with Groq API...");

        groqClient.fetchEligibleModels(apiKey, new GroqClient.ModelsCallback() {
            @Override
            public void onSuccess(List<String> eligibleModels) {
                // Save cached list to SharedPreferences
                String serialized = TextUtils.join(",", eligibleModels);
                getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_ELIGIBLE_MODELS, serialized)
                        .apply();

                GroqClient.setCachedEligibleModels(eligibleModels);

                // Preserve current selection if valid
                int pos = spModel.getSelectedItemPosition();
                String currKey = (pos >= 0 && pos < modelKeys.size()) ? modelKeys.get(pos) : "auto-smart";

                updateModelSpinner(eligibleModels, currKey);

                tvStatus.setText("✓ Found " + eligibleModels.size() + " eligible models for your account!");
                if (showToast) {
                    Toast.makeText(MainActivity.this, "Found " + eligibleModels.size() + " eligible models!", Toast.LENGTH_SHORT).show();
                }

                if (onComplete != null) {
                    onComplete.run();
                }
            }

            @Override
            public void onError(String error) {
                tvStatus.setText("⚠ Groq error checking eligibility: " + error);
                if (showToast) {
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
