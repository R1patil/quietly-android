package com.quietly.keyboard;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_SETTINGS = "quietly_settings";
    private static final String KEY_API_KEY = "groq_api_key";
    private static final String KEY_MODEL = "groq_model";

    private final List<String> MODELS = Arrays.asList(
            "llama-3.3-70b-versatile",
            "qwen/qwen3.8-27b",
            "llama3-70b-8192",
            "llama3-8b-8192",
            "gemma2-9b-it",
            "mixtral-8x7b-32768"
    );

    private EditText etApiKey;
    private Spinner spModel;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etApiKey = findViewById(R.id.et_api_key);
        spModel = findViewById(R.id.sp_model);
        tvStatus = findViewById(R.id.tv_status);

        // Populate Model dropdown
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, MODELS);
        spModel.setAdapter(adapter);

        // Load saved settings
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        String savedKey = prefs.getString(KEY_API_KEY, "");
        String savedModel = prefs.getString(KEY_MODEL, "llama-3.3-70b-versatile");

        etApiKey.setText(savedKey);
        int modelIdx = MODELS.indexOf(savedModel);
        if (modelIdx >= 0) spModel.setSelection(modelIdx);

        if (!savedKey.isEmpty()) {
            tvStatus.setText("✓ Groq API Key Saved (" + savedModel + ")");
        }

        // Save & Test Button
        Button btnSave = findViewById(R.id.btn_save);
        btnSave.setOnClickListener(v -> {
            String key = etApiKey.getText().toString().trim();
            String model = (String) spModel.getSelectedItem();

            if (!key.startsWith("gsk_")) {
                Toast.makeText(this, "Key should start with gsk_...", Toast.LENGTH_SHORT).show();
            }

            prefs.edit()
                    .putString(KEY_API_KEY, key)
                    .putString(KEY_MODEL, model)
                    .apply();

            tvStatus.setText("✓ Saved! Testing connection to Groq...");

            // Test connection
            GroqClient testClient = new GroqClient();
            testClient.generateReplies(key, model, "Warm", "Hello there!", "", new GroqClient.Callback() {
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

        // 3. Launch Clean YouTube
        Button btnLaunchYoutube = findViewById(R.id.btn_launch_youtube);
        btnLaunchYoutube.setOnClickListener(v -> {
            Intent intent = new Intent(this, YouTubeActivity.class);
            startActivity(intent);
        });
    }
}
