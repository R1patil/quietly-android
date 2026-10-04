package com.quietly.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.List;

public class QuietlyInputMethodService extends InputMethodService implements KeyboardLayoutHelper.KeyListener {

    private static final String PREFS_SETTINGS = "quietly_settings";
    private static final String KEY_API_KEY = "groq_api_key";
    private static final String KEY_MODEL = "groq_model";

    private final String[] TONES = {"Warm 🌟", "Formal 💼", "Direct ⚡", "Fix ✍️"};
    private int currentToneIndex = 0;

    private LinearLayout chipsContainer;
    private TextView btnTone;
    private TextView btnAiSuggest;

    private GroqClient groqClient;
    private KeyboardLayoutHelper layoutHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        groqClient = new GroqClient();
    }

    @Override
    public View onCreateInputView() {
        View view = getLayoutInflater().inflate(R.layout.keyboard_view, null);

        chipsContainer = view.findViewById(R.id.chips_container);
        btnTone = view.findViewById(R.id.btn_tone);
        btnAiSuggest = view.findViewById(R.id.btn_ai_suggest);

        btnTone.setText(TONES[currentToneIndex]);
        btnTone.setOnClickListener(v -> cycleTone());

        btnAiSuggest.setOnClickListener(v -> triggerAiSuggestions(false));

        LinearLayout row1 = view.findViewById(R.id.row1);
        LinearLayout row2 = view.findViewById(R.id.row2);
        LinearLayout row3 = view.findViewById(R.id.row3);
        LinearLayout row4 = view.findViewById(R.id.row4);

        layoutHelper = new KeyboardLayoutHelper(this, this);
        layoutHelper.buildKeyboard(row1, row2, row3, row4);

        return view;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        // If there's a fresh WhatsApp notification, show ready prompt
        String recentIncoming = WhatsAppNotificationService.getRecentIncomingMessage(this);
        if (!recentIncoming.isEmpty() && chipsContainer != null && chipsContainer.getChildCount() == 0) {
            btnAiSuggest.setText("✨ Reply: " + recentIncoming.substring(0, Math.min(18, recentIncoming.length())) + "…");
        } else if (btnAiSuggest != null) {
            btnAiSuggest.setText("✨ Suggest");
        }
    }

    private void cycleTone() {
        currentToneIndex = (currentToneIndex + 1) % TONES.length;
        btnTone.setText(TONES[currentToneIndex]);
        triggerAiSuggestions(true);
    }

    private void triggerAiSuggestions(boolean isToneChange) {
        SharedPreferences prefs = getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE);
        String apiKey = prefs.getString(KEY_API_KEY, "");
        String model = prefs.getString(KEY_MODEL, "llama-3.3-70b-versatile");

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "Please set your Groq API key in Quietly app", Toast.LENGTH_SHORT).show();
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        String draft = "";
        if (ic != null) {
            CharSequence textBefore = ic.getTextBeforeCursor(300, 0);
            if (textBefore != null) draft = textBefore.toString();
        }

        String incomingContext = WhatsAppNotificationService.getRecentIncomingMessage(this);
        String tone = TONES[currentToneIndex].split(" ")[0]; // "Warm", "Formal", etc.

        btnAiSuggest.setText("⏳ Thinking…");
        chipsContainer.removeAllViews();

        groqClient.generateReplies(apiKey, model, tone, incomingContext, draft, new GroqClient.Callback() {
            @Override
            public void onSuccess(List<String> suggestions) {
                btnAiSuggest.setText("✨ Suggest");
                displaySuggestions(suggestions);
            }

            @Override
            public void onError(String error) {
                btnAiSuggest.setText("✨ Suggest");
                Toast.makeText(QuietlyInputMethodService.this, "Groq: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displaySuggestions(List<String> suggestions) {
        if (chipsContainer == null) return;
        chipsContainer.removeAllViews();

        for (final String suggestion : suggestions) {
            TextView chip = new TextView(this);
            chip.setText(suggestion);
            chip.setTextColor(ContextCompat.getColor(this, R.color.chip_text));
            chip.setTextSize(12f);
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setPadding(24, 10, 24, 10);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(6, 0, 6, 0);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    // Replace draft if any, or just commit suggestion
                    ic.commitText(suggestion, 1);
                }
                chipsContainer.removeAllViews();
            });

            chipsContainer.addView(chip);
        }
    }

    // --- KeyListener implementation ---

    @Override
    public void onKeyChar(char c) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(String.valueOf(c), 1);
        }
    }

    @Override
    public void onKeyBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            CharSequence selectedText = ic.getSelectedText(0);
            if (selectedText != null && selectedText.length() > 0) {
                ic.commitText("", 1);
            } else {
                ic.deleteSurroundingText(1, 0);
            }
        }
    }

    @Override
    public void onKeyEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
    }

    @Override
    public void onKeySpace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(" ", 1);
        }
    }

    @Override
    public void onKeyShift() {
        // Handled in layoutHelper
    }
}
