package com.quietly.keyboard;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

public class KeyboardLayoutHelper {

    public interface KeyListener {
        void onKeyChar(char c);
        void onKeyBackspace();
        void onKeyEnter();
        void onKeySpace();
        void onKeyShift();
    }

    private static final String ROW1_LOWER = "qwertyuiop";
    private static final String ROW2_LOWER = "asdfghjkl";
    private static final String ROW3_LOWER = "zxcvbnm";

    private final Context context;
    private final KeyListener listener;
    private boolean isShifted = false;

    private final Button[] letterButtons = new Button[26];
    private int letterIndex = 0;

    public KeyboardLayoutHelper(Context context, KeyListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void buildKeyboard(LinearLayout row1, LinearLayout row2, LinearLayout row3, LinearLayout row4) {
        letterIndex = 0;
        row1.removeAllViews();
        row2.removeAllViews();
        row3.removeAllViews();
        row4.removeAllViews();

        // Row 1: q w e r t y u i o p
        for (char c : ROW1_LOWER.toCharArray()) {
            row1.addView(createKeyButton(c, 1.0f));
        }

        // Row 2: a s d f g h j k l
        for (char c : ROW2_LOWER.toCharArray()) {
            row2.addView(createKeyButton(c, 1.0f));
        }

        // Row 3: Shift (1.4f), z x c v b n m, Backspace (1.4f)
        Button shiftBtn = createActionButton("⇧", 1.4f, v -> {
            isShifted = !isShifted;
            updateShiftState();
            listener.onKeyShift();
        });
        row3.addView(shiftBtn);

        for (char c : ROW3_LOWER.toCharArray()) {
            row3.addView(createKeyButton(c, 1.0f));
        }

        Button backspaceBtn = createActionButton("⌫", 1.4f, v -> listener.onKeyBackspace());
        row3.addView(backspaceBtn);

        // Row 4: ?123, Comma, Space (4.0f), Period, Enter (1.5f)
        Button symBtn = createActionButton("123", 1.3f, v -> {
            // For quick numbers
            listener.onKeyChar('?');
        });
        row4.addView(symBtn);

        row4.addView(createSpecialCharButton(',', 1.0f));

        Button spaceBtn = createActionButton("Space", 4.2f, v -> listener.onKeySpace());
        row4.addView(spaceBtn);

        row4.addView(createSpecialCharButton('.', 1.0f));

        Button enterBtn = createActionButton("↵", 1.5f, v -> listener.onKeyEnter());
        enterBtn.setBackgroundResource(R.drawable.key_background_action);
        row4.addView(enterBtn);
    }

    private Button createKeyButton(final char lowerChar, float weight) {
        Button btn = new Button(context);
        btn.setText(String.valueOf(isShifted ? Character.toUpperCase(lowerChar) : lowerChar));
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
            char toType = isShifted ? Character.toUpperCase(lowerChar) : lowerChar;
            listener.onKeyChar(toType);
            if (isShifted) {
                isShifted = false;
                updateShiftState();
            }
        });

        if (letterIndex < letterButtons.length) {
            letterButtons[letterIndex++] = btn;
        }

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

        btn.setOnClickListener(v -> listener.onKeyChar(c));
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

        btn.setOnClickListener(onClick);
        return btn;
    }

    private void updateShiftState() {
        for (Button btn : letterButtons) {
            if (btn != null) {
                String text = btn.getText().toString();
                btn.setText(isShifted ? text.toUpperCase() : text.toLowerCase());
            }
        }
    }
}
