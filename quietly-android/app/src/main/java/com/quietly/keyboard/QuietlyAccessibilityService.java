package com.quietly.keyboard;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class QuietlyAccessibilityService extends AccessibilityService {

    public static final String PREFS_NAME = "quietly_context";
    public static final String KEY_LIVE_CHAT_CONTEXT = "live_chat_context";
    public static final String KEY_LIVE_CHAT_TIME = "live_chat_time";
    public static final String KEY_PACKAGE_NAME = "live_chat_package";

    private long lastScanTime = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        // Rate limit scanning to once per 500ms
        long now = System.currentTimeMillis();
        if (now - lastScanTime < 500) return;
        lastScanTime = now;

        String pkg = event.getPackageName().toString();
        if (!pkg.contains("whatsapp") && !pkg.contains("telegram") && !pkg.contains("messaging")) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            List<String> textNodes = new ArrayList<>();
            collectTextNodes(root, textNodes);

            if (!textNodes.isEmpty()) {
                // Find meaningful message texts from the chat screen
                List<String> chatMessages = filterChatMessages(textNodes);
                if (!chatMessages.isEmpty()) {
                    // The last meaningful message in the chat
                    String latestMessage = chatMessages.get(chatMessages.size() - 1);

                    // If we have previous history (2-3 messages), construct dialogue context
                    StringBuilder fullContext = new StringBuilder();
                    int startIdx = Math.max(0, chatMessages.size() - 3);
                    for (int i = startIdx; i < chatMessages.size(); i++) {
                        if (fullContext.length() > 0) fullContext.append(" -> ");
                        fullContext.append(chatMessages.get(i));
                    }

                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit()
                            .putString(KEY_LIVE_CHAT_CONTEXT, latestMessage)
                            .putString("live_chat_full_history", fullContext.toString())
                            .putString(KEY_PACKAGE_NAME, pkg)
                            .putLong(KEY_LIVE_CHAT_TIME, System.currentTimeMillis())
                            .apply();
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                root.recycle();
            } catch (Exception ignored) {}
        }
    }

    private void collectTextNodes(AccessibilityNodeInfo node, List<String> texts) {
        if (node == null) return;

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            String str = text.toString().trim();
            if (!str.isEmpty() && str.length() < 500) {
                texts.add(str);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectTextNodes(child, texts);
                try {
                    child.recycle();
                } catch (Exception ignored) {}
            }
        }
    }

    private List<String> filterChatMessages(List<String> rawTexts) {
        List<String> filtered = new ArrayList<>();
        for (String text : rawTexts) {
            String lower = text.toLowerCase();
            // Skip UI labels, placeholders, timestamps, status text
            if (lower.equals("message") || lower.equals("type a message") || lower.equals("online")
                    || lower.equals("typing…") || lower.equals("typing...") || lower.matches("^\\d{1,2}:\\d{2}(\\s?(am|pm))?$")
                    || lower.equals("yesterday") || lower.equals("today")
                    || lower.startsWith("http://") || lower.startsWith("https://")) {
                continue;
            }
            // Skip single punctuation or emojis alone
            if (text.length() <= 1 && !Character.isLetterOrDigit(text.charAt(0))) {
                continue;
            }
            filtered.add(text);
        }
        return filtered;
    }

    public static String getLiveChatContext(Context context, String currentPackage) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long time = prefs.getLong(KEY_LIVE_CHAT_TIME, 0);
        String savedPkg = prefs.getString(KEY_PACKAGE_NAME, "");

        // If user is not currently in the same chat app where context was captured, DO NOT use it!
        if (currentPackage != null && !currentPackage.isEmpty()) {
            if (!savedPkg.isEmpty() && !currentPackage.equalsIgnoreCase(savedPkg)) {
                return ""; // Context from different app (e.g. WhatsApp context in Google/X)
            }
        }

        // Expire after 90 seconds of inactivity to keep context fresh
        if (System.currentTimeMillis() - time < 90 * 1000) {
            String history = prefs.getString("live_chat_full_history", "");
            String latest = prefs.getString(KEY_LIVE_CHAT_CONTEXT, "");
            return !history.isEmpty() ? history : latest;
        }
        return "";
    }

    public static String getLiveChatContext(Context context) {
        return getLiveChatContext(context, "");
    }

    @Override
    public void onInterrupt() {
    }
}
