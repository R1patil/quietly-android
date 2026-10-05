package com.quietly.keyboard;

import android.content.ClipData;
import android.content.ClipboardManager;
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

public class QuietlyInputMethodService extends InputMethodService implements KeyboardLayoutHelper.KeyListener, VoiceInputHelper.VoiceCallback {

    private static final String PREFS_SETTINGS = "quietly_settings";
    private static final String KEY_API_KEY = "groq_api_key";
    private static final String KEY_MODEL = "groq_model";

    private final String[] TONES = {"Warm 🌟", "Formal 💼", "Direct ⚡", "Fix ✍️"};
    private int currentToneIndex = 0;

    private LinearLayout chipsContainer;
    private TextView btnTone;
    private TextView btnAiSuggest;
    private TextView btnVoice;

    private GroqClient groqClient;
    private KeyboardLayoutHelper layoutHelper;
    private VoiceInputHelper voiceInputHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        groqClient = new GroqClient();
        voiceInputHelper = new VoiceInputHelper(this, this);
    }

    @Override
    public View onCreateInputView() {
        View view = getLayoutInflater().inflate(R.layout.keyboard_view, null);

        chipsContainer = view.findViewById(R.id.chips_container);
        btnTone = view.findViewById(R.id.btn_tone);
        btnAiSuggest = view.findViewById(R.id.btn_ai_suggest);
        btnVoice = view.findViewById(R.id.btn_voice);

        btnTone.setText(TONES[currentToneIndex]);
        btnTone.setOnClickListener(v -> cycleTone());

        btnAiSuggest.setOnClickListener(v -> triggerAiSuggestions(false));

        if (btnVoice != null) {
            btnVoice.setOnClickListener(v -> toggleVoiceInput());
        }

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

        // Fetch best live or notification context
        String context = getBestAvailableContext();
        if (!context.isEmpty() && chipsContainer != null && chipsContainer.getChildCount() == 0) {
            String preview = context.length() > 18 ? context.substring(0, 18) + "…" : context;
            btnAiSuggest.setText("✨ Reply: \"" + preview + "\"");
        } else if (btnAiSuggest != null) {
            btnAiSuggest.setText("✨ Suggest");
        }
    }

    private String getBestAvailableContext() {
        // Priority 1: Real-time on-screen chat context via Accessibility
        String liveChat = QuietlyAccessibilityService.getLiveChatContext(this);
        if (!liveChat.isEmpty()) {
            return liveChat;
        }

        // Priority 2: WhatsApp / Telegram incoming notification
        String notifMsg = WhatsAppNotificationService.getRecentIncomingMessage(this);
        if (!notifMsg.isEmpty()) {
            return notifMsg;
        }

        // Priority 3: System clipboard if user copied something recently
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    if (text != null && text.length() > 0 && text.length() < 300) {
                        return text.toString().trim();
                    }
                }
            }
        } catch (Exception ignored) {}

        return "";
    }

    private void cycleTone() {
        currentToneIndex = (currentToneIndex + 1) % TONES.length;
        btnTone.setText(TONES[currentToneIndex]);
        triggerAiSuggestions(true);
    }

    private void toggleVoiceInput() {
        if (voiceInputHelper.isListening()) {
            voiceInputHelper.stopListening();
        } else {
            String lang = (layoutHelper != null) ? layoutHelper.getCurrentLanguageCode() : "en";
            voiceInputHelper.startListening(lang);
        }
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

        String context = getBestAvailableContext();
        String tone = TONES[currentToneIndex].split(" ")[0]; // "Warm", "Formal", etc.
        String lang = (layoutHelper != null) ? layoutHelper.getCurrentLanguageCode() : "auto";

        btnAiSuggest.setText("⏳ Thinking…");
        chipsContainer.removeAllViews();

        groqClient.generateReplies(apiKey, model, tone, context, draft, lang, new GroqClient.Callback() {
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
            chip.setTextColor(ContextCompat.getColor(this, R.color.on_primary));
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextSize(12f);
            chip.setPadding(24, 12, 24, 12);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(6, 0, 6, 0);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.commitText(suggestion, 1);
                }
            });

            chipsContainer.addView(chip);
        }
    }

    // --- KeyboardLayoutHelper.KeyListener implementation ---

    @Override
    public void onKeyChar(char c) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(String.valueOf(c), 1);
        }
    }

    @Override
    public void onKeyString(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(s, 1);
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
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
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
        // Layout helper shifts internally
    }

    @Override
    public void onLanguageChanged(String langCode, String langName) {
        Toast.makeText(this, "Language: " + langName, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onVoiceInputClicked() {
        toggleVoiceInput();
    }

    // --- VoiceInputHelper.VoiceCallback implementation ---

    @Override
    public void onSpeechText(String text, boolean isFinal) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && text != null && !text.isEmpty()) {
            ic.commitText(text + (isFinal ? " " : ""), 1);
        }
    }

    @Override
    public void onListeningStateChanged(boolean isListening) {
        if (btnVoice != null) {
            btnVoice.setText(isListening ? "🔴 Speak" : "🎙️");
        }
    }

    @Override
    public void onError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        if (btnVoice != null) {
            btnVoice.setText("🎙️");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (voiceInputHelper != null) {
            voiceInputHelper.destroy();
        }
    }
}
