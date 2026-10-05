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
    private TextView tvContextPreview;

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
        tvContextPreview = view.findViewById(R.id.tv_context_preview);

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

        setupInitialSuggestions();
        return view;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        // Fetch best live or notification context
        String context = getBestAvailableContext();
        if (!context.isEmpty()) {
            String preview = context.length() > 24 ? context.substring(0, 24) + "…" : context;
            if (tvContextPreview != null) {
                tvContextPreview.setText("💬 Context: \"" + preview + "\"");
            }
        } else {
            if (tvContextPreview != null) {
                tvContextPreview.setText("💡 Tap ✨ Suggest to generate 3 replies");
            }
        }
        updateWordSuggestions();
    }

    private void showReplyPromptChip(String context) {
        if (chipsContainer == null) return;
        chipsContainer.removeAllViews();

        TextView promptChip = new TextView(this);
        String preview = context.length() > 30 ? context.substring(0, 30) + "…" : context;
        promptChip.setText("✨ Reply to: \"" + preview + "\" (Tap to Generate)");
        promptChip.setTextColor(ContextCompat.getColor(this, R.color.chip_sparkle));
        promptChip.setBackgroundResource(R.drawable.chip_background_sparkle);
        promptChip.setTextSize(12f);
        promptChip.setGravity(android.view.Gravity.CENTER);
        promptChip.setPadding(12, 4, 12, 4);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        lp.setMargins(4, 2, 4, 2);
        promptChip.setLayoutParams(lp);

        promptChip.setOnClickListener(v -> triggerAiSuggestions(false));
        chipsContainer.addView(promptChip);
    }

    private void setupInitialSuggestions() {
        if (chipsContainer == null) return;
        chipsContainer.removeAllViews();

        String[] quickOptions = {"✨ Tap Suggest", "🎙️ Voice Type", "🌐 Switch Lang"};
        for (String opt : quickOptions) {
            TextView chip = new TextView(this);
            chip.setText(opt);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextSize(11f);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(6, 2, 6, 2);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1.0f
            );
            lp.setMargins(3, 2, 3, 2);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                if (opt.contains("Suggest")) triggerAiSuggestions(false);
                else if (opt.contains("Voice")) toggleVoiceInput();
                else if (opt.contains("Lang") && layoutHelper != null) layoutHelper.cycleLanguage();
            });

            chipsContainer.addView(chip);
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
            CharSequence textBefore = ic.getTextBeforeCursor(500, 0);
            if (textBefore != null) draft = textBefore.toString().trim();
        }
        final boolean isDraftMode = !draft.isEmpty();

        String context = getBestAvailableContext();
        String tone = TONES[currentToneIndex].split(" ")[0]; // "Warm", "Formal", etc.
        String lang = (layoutHelper != null) ? layoutHelper.getCurrentLanguageCode() : "auto";

        btnAiSuggest.setText("⏳ Thinking…");
        chipsContainer.removeAllViews();

        TextView loadingChip = new TextView(this);
        loadingChip.setText(isDraftMode ? "⏳ Polishing your draft into 3 variants…" : "⏳ Thinking… Drafting 3 context-aware replies…");
        loadingChip.setTextColor(ContextCompat.getColor(this, R.color.chip_sparkle));
        loadingChip.setBackgroundResource(R.drawable.chip_background);
        loadingChip.setTextSize(11f);
        loadingChip.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lpLoading = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        lpLoading.setMargins(4, 2, 4, 2);
        loadingChip.setLayoutParams(lpLoading);
        chipsContainer.addView(loadingChip);

        groqClient.generateReplies(apiKey, model, tone, context, draft, lang, new GroqClient.Callback() {
            @Override
            public void onSuccess(List<String> suggestions) {
                updateDraftState();
                displaySuggestions(suggestions, isDraftMode);
            }

            @Override
            public void onError(String error) {
                updateDraftState();
                Toast.makeText(QuietlyInputMethodService.this, "Groq: " + error, Toast.LENGTH_SHORT).show();
                setupInitialSuggestions();
            }
        });
    }

    private void displaySuggestions(List<String> suggestions, final boolean isDraftMode) {
        if (chipsContainer == null) return;
        chipsContainer.removeAllViews();

        for (final String suggestion : suggestions) {
            TextView chip = new TextView(this);
            chip.setText(suggestion);
            chip.setTextColor(ContextCompat.getColor(this, R.color.on_primary));
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextSize(11f);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setMaxLines(2);
            chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
            chip.setPadding(8, 2, 8, 2);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1.0f // Equal 1/3 screen width for each suggestion!
            );
            lp.setMargins(3, 2, 3, 2);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    if (isDraftMode) {
                        // Replace user's typed draft with chosen polished variant
                        CharSequence before = ic.getTextBeforeCursor(1000, 0);
                        if (before != null && before.length() > 0) {
                            ic.deleteSurroundingText(before.length(), 0);
                        }
                    }
                    ic.commitText(suggestion, 1);
                    updateDraftState();
                }
            });

            chipsContainer.addView(chip);
        }
    }

    private void updateDraftState() {
        InputConnection ic = getCurrentInputConnection();
        String draft = "";
        if (ic != null) {
            CharSequence textBefore = ic.getTextBeforeCursor(300, 0);
            if (textBefore != null) draft = textBefore.toString().trim();
        }

        if (!draft.isEmpty()) {
            if (btnAiSuggest != null) {
                btnAiSuggest.setText("✨ Polish Draft");
            }
            if (tvContextPreview != null) {
                String preview = draft.length() > 24 ? draft.substring(0, 24) + "…" : draft;
                tvContextPreview.setText("✍️ Polishing: \"" + preview + "\"");
            }
        } else {
            if (btnAiSuggest != null) {
                btnAiSuggest.setText("✨ Suggest");
            }
            String context = getBestAvailableContext();
            if (tvContextPreview != null) {
                if (!context.isEmpty()) {
                    String preview = context.length() > 24 ? context.substring(0, 24) + "…" : context;
                    tvContextPreview.setText("💬 Context: \"" + preview + "\"");
                } else {
                    tvContextPreview.setText("💡 Tap ✨ Suggest to generate 3 replies");
                }
            }
        }
    }

    // --- Real-time Word Suggestions Bar ---

    private void updateWordSuggestions() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || chipsContainer == null) return;

        String currentWord = WordSuggestionHelper.extractCurrentWord(ic);
        if (!currentWord.isEmpty()) {
            List<String> suggestions = WordSuggestionHelper.getSuggestions(currentWord, 3);
            if (!suggestions.isEmpty()) {
                displayWordSuggestions(suggestions, currentWord);
                return;
            }
        }

        // When no active word is being typed, show context or default shortcuts
        String context = getBestAvailableContext();
        if (!context.isEmpty()) {
            showReplyPromptChip(context);
        } else {
            setupInitialSuggestions();
        }
    }

    private void displayWordSuggestions(final List<String> suggestions, final String activeWord) {
        if (chipsContainer == null) return;
        chipsContainer.removeAllViews();

        for (final String suggestion : suggestions) {
            TextView chip = new TextView(this);
            chip.setText(suggestion);
            chip.setTextColor(ContextCompat.getColor(this, R.color.on_primary));
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextSize(13f);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding(10, 2, 10, 2);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1.0f
            );
            lp.setMargins(3, 2, 3, 2);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    if (activeWord != null && activeWord.length() > 0) {
                        ic.deleteSurroundingText(activeWord.length(), 0);
                    }
                    ic.commitText(suggestion + " ", 1);
                    updateDraftState();
                    updateWordSuggestions();
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
            updateDraftState();
            updateWordSuggestions();
        }
    }

    @Override
    public void onKeyString(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(s, 1);
            updateDraftState();
            updateWordSuggestions();
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
            updateDraftState();
            updateWordSuggestions();
        }
    }

    @Override
    public void onKeyEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
            updateDraftState();
            updateWordSuggestions();
        }
    }

    @Override
    public void onKeySpace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.commitText(" ", 1);
            updateDraftState();
            updateWordSuggestions();
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
        if (text == null || text.trim().isEmpty()) return;

        if (isFinal) {
            // Option A: Commit the clean single sentence once
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(text.trim() + " ", 1);
            }
            if (tvContextPreview != null) {
                tvContextPreview.setText("✓ Voice: \"" + text.trim() + "\"");
            }
        } else {
            // Live feedback on preview bar without writing repeatedly into WhatsApp
            if (tvContextPreview != null) {
                tvContextPreview.setText("🎙️ \"" + text + "…\"");
            }
        }
    }

    @Override
    public void onListeningStateChanged(boolean isListening) {
        if (btnVoice != null) {
            btnVoice.setText(isListening ? "🔴 Stop" : "🎙️");
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
