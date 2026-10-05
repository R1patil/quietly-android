package com.quietly.keyboard;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

public class KeyboardLayoutHelper {

    public interface KeyListener {
        void onKeyChar(char c);
        void onKeyString(String s);
        void onKeyBackspace();
        void onKeyEnter();
        void onKeySpace();
        void onKeyShift();
        void onLanguageChanged(String langCode, String langName);
        void onVoiceInputClicked();
    }

    // Supported Languages
    public static final String[] LANG_CODES = {"en", "hi", "kn"};
    public static final String[] LANG_NAMES = {"English", "हिंदी", "ಕನ್ನಡ"};

    private static final String ROW1_EN = "qwertyuiop";
    private static final String ROW2_EN = "asdfghjkl";
    private static final String ROW3_EN = "zxcvbnm";

    // Kannada basic script rows
    private static final String ROW1_KN = "ಅಆಇಈಉಊಋಎಏಐ";
    private static final String ROW2_KN = "ಕಖಗಘಙಚಛಜಝಞ";
    private static final String ROW3_KN = "ಟಠಡಢಣತಥದಧನ";

    // Hindi Devanagari script rows
    private static final String ROW1_HI = "अआइईउऊऋएऐओ";
    private static final String ROW2_HI = "कखगघङचछजझञ";
    private static final String ROW3_HI = "टठडढणतथदधन";

    // Number & Symbol layouts
    private static final String ROW1_SYM = "1234567890";
    private static final String ROW2_SYM = "@#₹%&*+-()";
    private static final String ROW3_SYM = "!\"':;/?~=";

    private final Context context;
    private final KeyListener listener;
    private final Vibrator vibrator;

    private boolean isShifted = false;
    private boolean isSymbolsMode = false;
    private int currentLangIndex = 0; // 0=EN, 1=HI, 2=KN

    private Button spaceBtn;
    private Button globeBtn;
    private LinearLayout r1, r2, r3, r4;

    public KeyboardLayoutHelper(Context context, KeyListener listener) {
        this.context = context;
        this.listener = listener;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public String getCurrentLanguageCode() {
        return LANG_CODES[currentLangIndex];
    }

    public String getCurrentLanguageName() {
        return LANG_NAMES[currentLangIndex];
    }

    public void buildKeyboard(LinearLayout row1, LinearLayout row2, LinearLayout row3, LinearLayout row4) {
        this.r1 = row1;
        this.r2 = row2;
        this.r3 = row3;
        this.r4 = row4;
        refreshLayout();
    }

    public void refreshLayout() {
        if (r1 == null || r2 == null || r3 == null || r4 == null) return;

        r1.removeAllViews();
        r2.removeAllViews();
        r3.removeAllViews();
        r4.removeAllViews();

        if (isSymbolsMode) {
            buildSymbolsLayout();
        } else {
            buildAlphabetLayout();
        }
    }

    private void buildAlphabetLayout() {
        String r1Chars, r2Chars, r3Chars;
        if (currentLangIndex == 1) { // Hindi
            r1Chars = ROW1_HI;
            r2Chars = ROW2_HI;
            r3Chars = ROW3_HI;
        } else if (currentLangIndex == 2) { // Kannada
            r1Chars = ROW1_KN;
            r2Chars = ROW2_KN;
            r3Chars = ROW3_KN;
        } else { // English
            r1Chars = ROW1_EN;
            r2Chars = ROW2_EN;
            r3Chars = ROW3_EN;
        }

        // Row 1
        for (char c : r1Chars.toCharArray()) {
            r1.addView(createKeyButton(c, 1.0f));
        }

        // Row 2
        for (char c : r2Chars.toCharArray()) {
            r2.addView(createKeyButton(c, 1.0f));
        }

        // Row 3: Shift / Switcher, characters, Backspace
        Button shiftBtn = createActionButton(isShifted ? "⬆" : "⇧", 1.3f, v -> {
            isShifted = !isShifted;
            refreshLayout();
            listener.onKeyShift();
        });
        r3.addView(shiftBtn);

        for (char c : r3Chars.toCharArray()) {
            r3.addView(createKeyButton(c, 1.0f));
        }

        Button backspaceBtn = createActionButton("⌫", 1.3f, v -> listener.onKeyBackspace());
        r3.addView(backspaceBtn);

        // Row 4: ?123, Globe, Space, Period, Enter
        Button symBtn = createActionButton("?123", 1.2f, v -> {
            isSymbolsMode = true;
            refreshLayout();
        });
        r4.addView(symBtn);

        globeBtn = createActionButton("🌐", 1.0f, v -> cycleLanguage());
        r4.addView(globeBtn);

        spaceBtn = createActionButton(LANG_NAMES[currentLangIndex], 3.8f, v -> listener.onKeySpace());
        spaceBtn.setOnLongClickListener(v -> {
            cycleLanguage();
            return true;
        });
        r4.addView(spaceBtn);

        r4.addView(createSpecialCharButton('.', 1.0f));

        Button enterBtn = createActionButton("↵", 1.4f, v -> listener.onKeyEnter());
        enterBtn.setBackgroundResource(R.drawable.key_background_action);
        r4.addView(enterBtn);
    }

    private void buildSymbolsLayout() {
        // Row 1: 1 2 3 4 5 6 7 8 9 0
        for (char c : ROW1_SYM.toCharArray()) {
            r1.addView(createKeyButton(c, 1.0f));
        }

        // Row 2: @ # ₹ % & * - + ( )
        for (char c : ROW2_SYM.toCharArray()) {
            r2.addView(createKeyButton(c, 1.0f));
        }

        // Row 3: Symbols shift, symbols, Backspace
        for (char c : ROW3_SYM.toCharArray()) {
            r3.addView(createKeyButton(c, 1.0f));
        }
        Button backspaceBtn = createActionButton("⌫", 1.3f, v -> listener.onKeyBackspace());
        r3.addView(backspaceBtn);

        // Row 4: ABC, Comma, Space, Period, Enter
        Button abcBtn = createActionButton("ABC", 1.3f, v -> {
            isSymbolsMode = false;
            refreshLayout();
        });
        r4.addView(abcBtn);

        r4.addView(createSpecialCharButton(',', 1.0f));

        Button space = createActionButton("Space", 3.8f, v -> listener.onKeySpace());
        r4.addView(space);

        r4.addView(createSpecialCharButton('.', 1.0f));

        Button enterBtn = createActionButton("↵", 1.4f, v -> listener.onKeyEnter());
        enterBtn.setBackgroundResource(R.drawable.key_background_action);
        r4.addView(enterBtn);
    }

    public void cycleLanguage() {
        currentLangIndex = (currentLangIndex + 1) % LANG_CODES.length;
        refreshLayout();
        listener.onLanguageChanged(LANG_CODES[currentLangIndex], LANG_NAMES[currentLangIndex]);
    }

    private Button createKeyButton(final char c, float weight) {
        Button btn = new Button(context);
        char displayChar = (isShifted && Character.isLetter(c)) ? Character.toUpperCase(c) : c;
        btn.setText(String.valueOf(displayChar));
        btn.setTextColor(ContextCompat.getColor(context, R.color.kb_key_text));
        btn.setTextSize(16f);
        btn.setAllCaps(false);
        btn.setBackgroundResource(R.drawable.key_background);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            triggerHaptic(v);
            char toType = (isShifted && Character.isLetter(c)) ? Character.toUpperCase(c) : c;
            listener.onKeyChar(toType);
            if (isShifted) {
                isShifted = false;
                refreshLayout();
            }
        });

        return btn;
    }

    private Button createSpecialCharButton(final char c, float weight) {
        Button btn = new Button(context);
        btn.setText(String.valueOf(c));
        btn.setTextColor(ContextCompat.getColor(context, R.color.kb_key_text));
        btn.setTextSize(16f);
        btn.setBackgroundResource(R.drawable.key_background);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            triggerHaptic(v);
            listener.onKeyChar(c);
        });
        return btn;
    }

    private Button createActionButton(String label, float weight, View.OnClickListener onClick) {
        Button btn = new Button(context);
        btn.setText(label);
        btn.setTextColor(ContextCompat.getColor(context, R.color.kb_key_text));
        btn.setTextSize(14f);
        btn.setBackgroundResource(R.drawable.key_background);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            triggerHaptic(v);
            onClick.onClick(v);
        });
        return btn;
    }

    private void triggerHaptic(View view) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Exception ignored) {}
    }
}
