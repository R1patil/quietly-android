package com.quietly.keyboard;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

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
        void onStickerSelected(StickerItem sticker);
    }

    // Supported Languages
    public static final String[] LANG_CODES = {"en", "hi", "kn"};
    public static final String[] LANG_NAMES = {"English", "हिंदी", "ಕನ್ನಡ"};

    // English layouts
    private static final String ROW1_EN = "qwertyuiop";
    private static final String ROW2_EN = "asdfghjkl";
    private static final String ROW3_EN = "zxcvbnm";

    private static final String ROW1_EN_SHIFT = "QWERTYUIOP";
    private static final String ROW2_EN_SHIFT = "ASDFGHJKL";
    private static final String ROW3_EN_SHIFT = "ZXCVBNM";

    // Kannada Unshifted: Primary Matras (including ಾ for ಕಾ) + First Consonants
    private static final String ROW1_KN = "ಾಿೀುೂೆೇೈೊ್";
    private static final String ROW2_KN = "ಕಖಗಘಙಚಛಜಝಞ";
    private static final String ROW3_KN = "ಟಠಡಢಣತಥದಧನಪ";

    // Kannada Shifted: Independent Vowels + Remaining Consonants + Secondary Signs
    private static final String ROW1_KN_SHIFT = "ಅಆಇಈಉಊಋಎಏಐ";
    private static final String ROW2_KN_SHIFT = "ಫಬಭಮಯರಲವಶಷ";
    private static final String ROW3_KN_SHIFT = "ಒಓಔಸಹಳೋೌಂಃ";

    // Hindi Unshifted: Primary Matras + First Consonants
    private static final String ROW1_HI = "ािीुूेैो्ं";
    private static final String ROW2_HI = "कखगघङचछजझञ";
    private static final String ROW3_HI = "टठडढणतथदधनप";

    // Hindi Shifted: Independent Vowels + Remaining Consonants
    private static final String ROW1_HI_SHIFT = "अआइईउऊऋएऐओ";
    private static final String ROW2_HI_SHIFT = "फबभमयरलवशष";
    private static final String ROW3_HI_SHIFT = "औौसहड़ढ़क्षज्ञः";

    // Number & Symbol layouts
    private static final String ROW1_SYM = "1234567890";
    private static final String ROW2_SYM = "@#₹%&*+-()";
    private static final String ROW3_SYM = "!\"':;/?~=";

    // Emoji Collections across 4 curated categories
    private static final String[][] EMOJI_CATEGORIES = {
        { // Category 0: Smileys & Emotions
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "🥰",
            "😍", "🤩", "😘", "😋", "😜", "🤪", "😎", "🥳", "😏", "🥺",
            "😭", "😤", "😡", "🤯", "😱", "😴", "🤔", "🤫"
        },
        { // Category 1: Gestures & Hearts
            "👍", "👎", "👏", "🙌", "🤝", "🙏", "✌️", "🤞", "🤟", "🤘",
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "💔", "❣️",
            "💕", "💞", "💓", "💗", "💖", "💘", "💝", "✨"
        },
        { // Category 2: Fire, Party & Food
            "🔥", "💯", "⚡", "💥", "🌟", "⭐", "🎉", "🎊", "🎈", "🎂",
            "🏆", "🥇", "👑", "💎", "🚀", "💰", "💵", "🍻", "🥂", "🍕",
            "🍔", "🍟", "🍿", "🍩", "🍦", "☕", "🎮", "🎵"
        },
        { // Category 3: Objects, Everyday & Animals
            "📱", "💻", "📷", "🎧", "🚗", "✈️", "🏖️", "☀️", "🌙", "⭐",
            "🌸", "🌹", "🌻", "🐶", "🐱", "🐼", "🦁", "🐵", "🦄", "🌈",
            "⚽", "🏀", "🎾", "🎲", "📚", "✏️", "💡", "🔔"
        }
    };
    private static final String[] EMOJI_CAT_ICONS = {"😀", "❤️", "🔥", "🍕"};

    private final Context context;
    private final KeyListener listener;
    private final AudioManager audioManager;

    private boolean isShifted = false;
    private boolean isSymbolsMode = false;
    private boolean isEmojiMode = false;
    private boolean isStickerMode = false;
    private int emojiCategoryIndex = 0;
    private int stickerCategoryIndex = 0;
    private int currentLangIndex = 0; // 0=EN, 1=HI, 2=KN

    private Button spaceBtn;
    private Button globeBtn;
    private Button shiftBtn;
    private LinearLayout r1, r2, r3, r4;

    // References to letter buttons for sub-millisecond shift updates without layout rebuild
    private final List<Button> r1LetterButtons = new ArrayList<>();
    private final List<Button> r2LetterButtons = new ArrayList<>();
    private final List<Button> r3LetterButtons = new ArrayList<>();

    public KeyboardLayoutHelper(Context context, KeyListener listener) {
        this.context = context;
        this.listener = listener;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
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
        r1LetterButtons.clear();
        r2LetterButtons.clear();
        r3LetterButtons.clear();

        if (isStickerMode) {
            buildStickerLayout();
        } else if (isEmojiMode) {
            buildEmojiLayout();
        } else if (isSymbolsMode) {
            buildSymbolsLayout();
        } else {
            buildAlphabetLayout();
        }
    }

    private void buildAlphabetLayout() {
        String r1Chars, r2Chars, r3Chars;
        if (currentLangIndex == 1) { // Hindi
            r1Chars = isShifted ? ROW1_HI_SHIFT : ROW1_HI;
            r2Chars = isShifted ? ROW2_HI_SHIFT : ROW2_HI;
            r3Chars = isShifted ? ROW3_HI_SHIFT : ROW3_HI;
        } else if (currentLangIndex == 2) { // Kannada
            r1Chars = isShifted ? ROW1_KN_SHIFT : ROW1_KN;
            r2Chars = isShifted ? ROW2_KN_SHIFT : ROW2_KN;
            r3Chars = isShifted ? ROW3_KN_SHIFT : ROW3_KN;
        } else { // English
            r1Chars = isShifted ? ROW1_EN_SHIFT : ROW1_EN;
            r2Chars = isShifted ? ROW2_EN_SHIFT : ROW2_EN;
            r3Chars = isShifted ? ROW3_EN_SHIFT : ROW3_EN;
        }

        // Row 1
        for (char c : r1Chars.toCharArray()) {
            Button btn = createKeyButton(c, 1.0f);
            r1.addView(btn);
            if (currentLangIndex == 0) r1LetterButtons.add(btn);
        }

        // Row 2
        for (char c : r2Chars.toCharArray()) {
            Button btn = createKeyButton(c, 1.0f);
            r2.addView(btn);
            if (currentLangIndex == 0) r2LetterButtons.add(btn);
        }

        // Row 3: Shift / Switcher, characters, Backspace
        shiftBtn = createActionButton(isShifted ? "⬆" : "⇧", 1.3f, v -> {
            toggleShift();
            listener.onKeyShift();
        });
        if (isShifted) {
            shiftBtn.setBackgroundResource(R.drawable.key_background_action);
        }
        r3.addView(shiftBtn);

        for (char c : r3Chars.toCharArray()) {
            Button btn = createKeyButton(c, 1.0f);
            r3.addView(btn);
            if (currentLangIndex == 0) r3LetterButtons.add(btn);
        }

        Button backspaceBtn = createRepeatingBackspaceButton(1.3f);
        r3.addView(backspaceBtn);

        // Row 4: ?123, Globe, Emoji, Space, Period, Enter
        Button symBtn = createActionButton("?123", 1.2f, v -> {
            isSymbolsMode = true;
            isEmojiMode = false;
            isStickerMode = false;
            refreshLayout();
        });
        r4.addView(symBtn);

        globeBtn = createActionButton("🌐", 0.9f, v -> cycleLanguage());
        r4.addView(globeBtn);

        Button emojiBtn = createActionButton("😊", 0.9f, v -> {
            isEmojiMode = true;
            isStickerMode = false;
            isSymbolsMode = false;
            refreshLayout();
        });
        r4.addView(emojiBtn);

        spaceBtn = createSpaceButton(LANG_NAMES[currentLangIndex], 3.8f);
        spaceBtn.setOnLongClickListener(v -> {
            cycleLanguage();
            return true;
        });
        r4.addView(spaceBtn);

        r4.addView(createSpecialCharButton('.', 0.9f));

        Button enterBtn = createEnterButton(1.3f);
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
        Button backspaceBtn = createRepeatingBackspaceButton(1.3f);
        r3.addView(backspaceBtn);

        // Row 4: ABC, Emoji, Comma, Space, Period, Enter
        Button abcBtn = createActionButton("ABC", 1.2f, v -> {
            isSymbolsMode = false;
            isEmojiMode = false;
            isStickerMode = false;
            refreshLayout();
        });
        r4.addView(abcBtn);

        Button emojiBtn = createActionButton("😊", 0.9f, v -> {
            isEmojiMode = true;
            isStickerMode = false;
            isSymbolsMode = false;
            refreshLayout();
        });
        r4.addView(emojiBtn);

        r4.addView(createSpecialCharButton(',', 0.9f));

        Button space = createSpaceButton("Space", 3.8f);
        r4.addView(space);

        r4.addView(createSpecialCharButton('.', 0.9f));

        Button enterBtn = createEnterButton(1.3f);
        r4.addView(enterBtn);
    }

    private void buildEmojiLayout() {
        String[] currentCategoryEmojis = EMOJI_CATEGORIES[emojiCategoryIndex];

        // Row 1: First 10 emojis
        for (int i = 0; i < 10 && i < currentCategoryEmojis.length; i++) {
            r1.addView(createEmojiButton(currentCategoryEmojis[i], 1.0f));
        }

        // Row 2: Next 10 emojis (index 10 to 19)
        for (int i = 10; i < 20 && i < currentCategoryEmojis.length; i++) {
            r2.addView(createEmojiButton(currentCategoryEmojis[i], 1.0f));
        }

        // Row 3: Remaining 8 emojis (index 20 to 27) + Backspace
        for (int i = 20; i < 28 && i < currentCategoryEmojis.length; i++) {
            r3.addView(createEmojiButton(currentCategoryEmojis[i], 1.0f));
        }
        Button backspaceBtn = createRepeatingBackspaceButton(1.4f);
        r3.addView(backspaceBtn);

        // Row 4: ABC, Sticker Switcher, Category Switchers, Space, Enter
        Button abcBtn = createActionButton("ABC", 1.1f, v -> {
            isEmojiMode = false;
            isStickerMode = false;
            isSymbolsMode = false;
            refreshLayout();
        });
        r4.addView(abcBtn);

        Button stickerTabBtn = createActionButton("🏷️", 0.9f, v -> {
            isEmojiMode = false;
            isStickerMode = true;
            refreshLayout();
        });
        r4.addView(stickerTabBtn);

        for (int i = 0; i < EMOJI_CAT_ICONS.length; i++) {
            final int catIdx = i;
            Button catBtn = createActionButton(EMOJI_CAT_ICONS[i], 0.9f, v -> {
                emojiCategoryIndex = catIdx;
                refreshLayout();
            });
            if (emojiCategoryIndex == catIdx) {
                catBtn.setBackgroundResource(R.drawable.key_background_action);
            }
            r4.addView(catBtn);
        }

        Button space = createSpaceButton("Space", 2.0f);
        r4.addView(space);

        Button enterBtn = createEnterButton(1.3f);
        r4.addView(enterBtn);
    }

    private void buildStickerLayout() {
        List<StickerItem> stickers = StickerHelper.getStickersForCategory(stickerCategoryIndex);

        // Row 1: 4 stickers (index 0 to 3)
        for (int i = 0; i < 4 && i < stickers.size(); i++) {
            r1.addView(createStickerButton(stickers.get(i), 1.0f));
        }

        // Row 2: 4 stickers (index 4 to 7)
        for (int i = 4; i < 8 && i < stickers.size(); i++) {
            r2.addView(createStickerButton(stickers.get(i), 1.0f));
        }

        // Row 3: 3 stickers (index 8 to 10) + Backspace
        for (int i = 8; i < 11 && i < stickers.size(); i++) {
            r3.addView(createStickerButton(stickers.get(i), 1.0f));
        }
        Button backspaceBtn = createRepeatingBackspaceButton(1.3f);
        r3.addView(backspaceBtn);

        // Row 4: ABC, Emoji Tab Switcher, Category Switchers, Enter
        Button abcBtn = createActionButton("ABC", 1.1f, v -> {
            isStickerMode = false;
            isEmojiMode = false;
            isSymbolsMode = false;
            refreshLayout();
        });
        r4.addView(abcBtn);

        Button emojiTabBtn = createActionButton("😊", 0.9f, v -> {
            isStickerMode = false;
            isEmojiMode = true;
            refreshLayout();
        });
        r4.addView(emojiTabBtn);

        for (int i = 0; i < StickerHelper.CATEGORY_ICONS.length; i++) {
            final int catIdx = i;
            Button catBtn = createActionButton(StickerHelper.CATEGORY_ICONS[i], 1.0f, v -> {
                stickerCategoryIndex = catIdx;
                refreshLayout();
            });
            if (stickerCategoryIndex == catIdx) {
                catBtn.setBackgroundResource(R.drawable.key_background_action);
            }
            r4.addView(catBtn);
        }

        Button space = createSpaceButton("Space", 1.8f);
        r4.addView(space);

        Button enterBtn = createEnterButton(1.3f);
        r4.addView(enterBtn);
    }

    /**
     * Toggles Shift state. In English, updates button labels in-place with zero view recreation
     * for instant typing speed.
     */
    private void toggleShift() {
        isShifted = !isShifted;
        if (currentLangIndex == 0 && !r1LetterButtons.isEmpty()) {
            updateShiftLabelsEnglish();
        } else {
            refreshLayout();
        }
    }

    private void updateShiftLabelsEnglish() {
        String r1Chars = isShifted ? ROW1_EN_SHIFT : ROW1_EN;
        String r2Chars = isShifted ? ROW2_EN_SHIFT : ROW2_EN;
        String r3Chars = isShifted ? ROW3_EN_SHIFT : ROW3_EN;

        for (int i = 0; i < r1LetterButtons.size() && i < r1Chars.length(); i++) {
            final char c = r1Chars.charAt(i);
            Button btn = r1LetterButtons.get(i);
            btn.setText(String.valueOf(c));
            btn.setOnClickListener(v -> handleCharClick(v, c));
        }

        for (int i = 0; i < r2LetterButtons.size() && i < r2Chars.length(); i++) {
            final char c = r2Chars.charAt(i);
            Button btn = r2LetterButtons.get(i);
            btn.setText(String.valueOf(c));
            btn.setOnClickListener(v -> handleCharClick(v, c));
        }

        for (int i = 0; i < r3LetterButtons.size() && i < r3Chars.length(); i++) {
            final char c = r3Chars.charAt(i);
            Button btn = r3LetterButtons.get(i);
            btn.setText(String.valueOf(c));
            btn.setOnClickListener(v -> handleCharClick(v, c));
        }

        if (shiftBtn != null) {
            shiftBtn.setText(isShifted ? "⬆" : "⇧");
            shiftBtn.setBackgroundResource(isShifted ? R.drawable.key_background_action : R.drawable.key_background);
        }
    }

    private void handleCharClick(View v, char c) {
        triggerFeedback(v, AudioManager.FX_KEYPRESS_STANDARD);
        listener.onKeyChar(c);
        if (isShifted && currentLangIndex == 0) {
            isShifted = false;
            updateShiftLabelsEnglish();
        }
    }

    public void cycleLanguage() {
        currentLangIndex = (currentLangIndex + 1) % LANG_CODES.length;
        isShifted = false;
        isSymbolsMode = false;
        isEmojiMode = false;
        isStickerMode = false;
        refreshLayout();
        listener.onLanguageChanged(LANG_CODES[currentLangIndex], LANG_NAMES[currentLangIndex]);
    }

    private Button createKeyButton(final char c, float weight) {
        Button btn = new Button(context);

        int type = Character.getType(c);
        boolean isCombiningMark = (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK);
        String displayLabel = isCombiningMark ? ("◌" + c) : String.valueOf(c);

        btn.setText(displayLabel);
        btn.setTextColor(ContextCompat.getColor(context, R.color.kb_key_text));
        btn.setTextSize(isCombiningMark ? 16f : 19f);
        btn.setAllCaps(false);
        btn.setBackgroundResource(R.drawable.key_background);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> handleCharClick(v, c));
        return btn;
    }

    private Button createEmojiButton(final String emoji, float weight) {
        Button btn = new Button(context);
        btn.setText(emoji);
        btn.setTextSize(22f);
        btn.setBackgroundResource(R.drawable.key_background);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            triggerFeedback(v, AudioManager.FX_KEYPRESS_STANDARD);
            listener.onKeyString(emoji);
        });
        return btn;
    }

    private Button createStickerButton(final StickerItem item, float weight) {
        Button btn = new Button(context);
        btn.setText(item.getEmoji() + " " + item.getTitle());
        btn.setTextColor(ContextCompat.getColor(context, R.color.kb_key_text));
        btn.setTextSize(10.5f);
        btn.setBackgroundResource(R.drawable.key_background);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(2, 0, 2, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            triggerFeedback(v, AudioManager.FX_KEYPRESS_STANDARD);
            listener.onStickerSelected(item);
        });
        return btn;
    }

    private Button createSpecialCharButton(final char c, float weight) {
        Button btn = new Button(context);
        btn.setText(String.valueOf(c));
        btn.setTextColor(ContextCompat.getColor(context, R.color.kb_key_text));
        btn.setTextSize(18f);
        btn.setBackgroundResource(R.drawable.key_background);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            triggerFeedback(v, AudioManager.FX_KEYPRESS_STANDARD);
            listener.onKeyChar(c);
        });
        return btn;
    }

    private Button createActionButton(String label, float weight, View.OnClickListener onClick) {
        Button btn = new Button(context);
        btn.setText(label);
        btn.setTextColor(ContextCompat.getColor(context, R.color.kb_key_text));
        btn.setTextSize(15f);
        btn.setBackgroundResource(R.drawable.key_background);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            triggerFeedback(v, AudioManager.FX_KEYPRESS_STANDARD);
            onClick.onClick(v);
        });
        return btn;
    }

    private Button createSpaceButton(String label, float weight) {
        Button btn = new Button(context);
        btn.setText(label);
        btn.setTextColor(ContextCompat.getColor(context, R.color.kb_key_text));
        btn.setTextSize(15f);
        btn.setBackgroundResource(R.drawable.key_background);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);

        btn.setOnClickListener(v -> {
            triggerFeedback(v, AudioManager.FX_KEYPRESS_SPACEBAR);
            listener.onKeySpace();
        });
        return btn;
    }

    private Button createEnterButton(float weight) {
        Button enterBtn = new Button(context);
        enterBtn.setText("↵");
        enterBtn.setTextColor(ContextCompat.getColor(context, R.color.kb_key_text));
        enterBtn.setTextSize(16f);
        enterBtn.setBackgroundResource(R.drawable.key_background_action);
        enterBtn.setGravity(Gravity.CENTER);
        enterBtn.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 0, 2, 0);
        enterBtn.setLayoutParams(lp);

        enterBtn.setOnClickListener(v -> {
            triggerFeedback(v, AudioManager.FX_KEYPRESS_RETURN);
            listener.onKeyEnter();
        });
        return enterBtn;
    }

    /**
     * Creates Backspace button with continuous deletion when held down.
     */
    private Button createRepeatingBackspaceButton(float weight) {
        Button btn = new Button(context);
        btn.setText("⌫");
        btn.setTextColor(ContextCompat.getColor(context, R.color.kb_key_text));
        btn.setTextSize(15f);
        btn.setBackgroundResource(R.drawable.key_background);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(0, 0, 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(2, 0, 2, 0);
        btn.setLayoutParams(lp);

        final Handler handler = new Handler(Looper.getMainLooper());
        final int initialDelay = 350; // ms before continuous delete begins
        final int repeatInterval = 45; // ms between consecutive deletes

        final Runnable repeatRunnable = new Runnable() {
            @Override
            public void run() {
                triggerFeedback(btn, AudioManager.FX_KEYPRESS_DELETE);
                listener.onKeyBackspace();
                handler.postDelayed(this, repeatInterval);
            }
        };

        btn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    triggerFeedback(v, AudioManager.FX_KEYPRESS_DELETE);
                    listener.onKeyBackspace();
                    handler.removeCallbacks(repeatRunnable);
                    handler.postDelayed(repeatRunnable, initialDelay);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    handler.removeCallbacks(repeatRunnable);
                    return true;
            }
            return false;
        });

        return btn;
    }

    /**
     * Triggers both haptic vibration and audible key click sound on each keystroke.
     */
    private void triggerFeedback(View view, int soundEffect) {
        try {
            if (view != null) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        } catch (Exception ignored) {}

        try {
            if (audioManager != null) {
                audioManager.playSoundEffect(soundEffect);
            }
        } catch (Exception ignored) {}
    }
}
