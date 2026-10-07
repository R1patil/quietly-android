package com.quietly.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.UserDictionary;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages loading and syncing words from Android's system UserDictionary
 * (android.provider.UserDictionary.Words) as well as local persistent user history.
 */
public class UserDictionaryHelper {

    private static final String TAG = "UserDictionaryHelper";
    private static final String PREF_USER_WORDS = "quietly_user_words";
    private static final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    /**
     * Loads words from Android's UserDictionary Content Provider and local preferences into the Trie.
     */
    public static void loadIntoTrie(Context context, Trie trie) {
        if (context == null || trie == null) return;

        // 1. Load locally learned words from SharedPreferences
        loadLocalLearnedWords(context, trie);

        // 2. Load from Android System UserDictionary in background thread
        backgroundExecutor.execute(() -> loadAndroidSystemDictionary(context, trie));
    }

    private static void loadLocalLearnedWords(Context context, Trie trie) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_USER_WORDS, Context.MODE_PRIVATE);
            Map<String, ?> allEntries = prefs.getAll();
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                String word = entry.getKey();
                Object val = entry.getValue();
                int freq = (val instanceof Integer) ? (Integer) val : 50;
                trie.insert(word, freq);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error loading local user words", e);
        }
    }

    private static void loadAndroidSystemDictionary(Context context, Trie trie) {
        try {
            Uri uri = UserDictionary.Words.CONTENT_URI;
            String[] projection = {
                UserDictionary.Words.WORD,
                UserDictionary.Words.FREQUENCY
            };

            Cursor cursor = context.getContentResolver().query(
                uri,
                projection,
                null,
                null,
                null
            );

            if (cursor != null) {
                int wordCol = cursor.getColumnIndex(UserDictionary.Words.WORD);
                int freqCol = cursor.getColumnIndex(UserDictionary.Words.FREQUENCY);

                int count = 0;
                while (cursor.moveToNext()) {
                    if (wordCol != -1) {
                        String word = cursor.getString(wordCol);
                        int freq = (freqCol != -1) ? cursor.getInt(freqCol) : 100;
                        if (word != null && !word.trim().isEmpty()) {
                            trie.insert(word.trim(), Math.max(freq, 20));
                            count++;
                        }
                    }
                }
                cursor.close();
                Log.d(TAG, "Loaded " + count + " words from Android UserDictionary.");
            }
        } catch (SecurityException se) {
            Log.w(TAG, "READ_USER_DICTIONARY permission not granted, skipping system dictionary.");
        } catch (Exception e) {
            Log.w(TAG, "Failed to query UserDictionary", e);
        }
    }

    /**
     * Learns a newly typed word: updates the in-memory Trie, saves to local preferences,
     * and attempts to register it in Android's system UserDictionary.
     */
    public static void learnWord(Context context, Trie trie, String word) {
        if (context == null || trie == null || word == null) return;
        final String cleanWord = word.trim();
        if (cleanWord.length() < 2 || cleanWord.length() > 30) return;

        // Skip words that contain pure digits or symbol-only strings
        boolean hasLetter = false;
        for (int i = 0; i < cleanWord.length(); i++) {
            if (Character.isLetter(cleanWord.charAt(i))) {
                hasLetter = true;
                break;
            }
        }
        if (!hasLetter) return;

        // 1. Immediately update Trie in memory (zero UI lag)
        trie.learnWord(cleanWord);

        // 2. Persist locally and sync to system dictionary on background worker
        backgroundExecutor.execute(() -> {
            try {
                // Update SharedPreferences
                SharedPreferences prefs = context.getSharedPreferences(PREF_USER_WORDS, Context.MODE_PRIVATE);
                int currentFreq = prefs.getInt(cleanWord.toLowerCase(), 10);
                prefs.edit().putInt(cleanWord.toLowerCase(), currentFreq + 5).apply();

                // Add to Android system UserDictionary if available
                try {
                    UserDictionary.Words.addWord(
                        context,
                        cleanWord,
                        Math.min(255, currentFreq + 10),
                        UserDictionary.Words.LOCALE_TYPE_ALL
                    );
                } catch (SecurityException ignored) {
                    // Handled if WRITE_USER_DICTIONARY is restricted
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed persisting learned word: " + cleanWord, e);
            }
        });
    }
}
