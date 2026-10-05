package com.quietly.keyboard;

import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WordSuggestionHelper {

    // Curated high-frequency English + common conversational words
    private static final String[] VOCABULARY = {
        "a", "about", "above", "across", "after", "again", "against", "all", "almost", "alone",
        "along", "already", "also", "always", "am", "among", "an", "and", "another", "answer",
        "any", "anyone", "anything", "anyway", "app", "appreciate", "are", "around", "as", "ask",
        "at", "away", "awesome", "baby", "back", "bad", "be", "beautiful", "because", "become",
        "been", "before", "began", "begin", "behind", "being", "believe", "best", "better", "between",
        "big", "birthday", "bit", "black", "body", "book", "both", "boy", "break", "bring",
        "brother", "brought", "build", "busy", "but", "by", "call", "came", "can", "cannot",
        "car", "care", "carry", "case", "catch", "change", "chat", "check", "child", "children",
        "city", "class", "clean", "clear", "close", "cold", "come", "coming", "company", "complete",
        "congratulations", "cool", "could", "country", "course", "crazy", "cute", "dad", "day", "days",
        "deal", "dear", "decide", "definitely", "did", "different", "dinner", "do", "does", "doing",
        "done", "dont", "door", "down", "drive", "during", "each", "early", "easy", "eat",
        "either", "else", "email", "end", "enjoy", "enough", "even", "evening", "ever", "every",
        "everyone", "everything", "exactly", "example", "excited", "expect", "face", "fact", "family", "far",
        "fast", "father", "feel", "feeling", "few", "field", "finally", "find", "fine", "first",
        "five", "follow", "food", "for", "forget", "forward", "found", "four", "free", "friend",
        "friends", "from", "full", "fun", "funny", "game", "gave", "get", "getting", "girl",
        "give", "glad", "go", "god", "going", "gone", "good", "got", "great", "group",
        "grow", "guess", "guy", "guys", "had", "half", "hand", "happen", "happy", "hard",
        "has", "have", "having", "he", "head", "hear", "heard", "heart", "hello", "help",
        "her", "here", "hey", "hi", "high", "him", "himself", "his", "hold", "home",
        "hope", "hot", "hour", "house", "how", "however", "huge", "idea", "if", "important",
        "in", "inside", "instead", "into", "is", "issue", "it", "its", "job", "join",
        "just", "keep", "kept", "kind", "knew", "know", "known", "large", "last", "late",
        "later", "laugh", "learn", "leave", "leaving", "left", "less", "let", "life", "light",
        "like", "likely", "line", "link", "little", "live", "long", "look", "looking", "lost",
        "lot", "love", "lunch", "made", "make", "making", "man", "many", "matter", "may",
        "maybe", "me", "mean", "meet", "meeting", "message", "might", "mind", "minute", "miss",
        "moment", "money", "month", "more", "morning", "most", "move", "much", "music", "must",
        "my", "myself", "name", "near", "need", "never", "new", "next", "nice", "night",
        "no", "nobody", "none", "noone", "not", "nothing", "now", "number", "off", "office",
        "often", "oh", "ok", "okay", "old", "on", "once", "one", "only", "open",
        "or", "order", "other", "others", "our", "out", "outside", "over", "own", "part",
        "party", "past", "pay", "people", "per", "perfect", "perhaps", "person", "phone", "photo",
        "pick", "pic", "pics", "picture", "place", "plan", "play", "please", "point", "possible",
        "post", "pretty", "probably", "problem", "project", "put", "quick", "quite", "read", "ready",
        "real", "really", "reason", "remember", "reply", "right", "room", "run", "said", "same",
        "saw", "say", "saying", "school", "see", "seem", "seen", "send", "sent", "set",
        "several", "shall", "she", "short", "should", "show", "side", "simple", "since", "sit",
        "sleep", "small", "so", "some", "someone", "something", "sometimes", "somewhere", "soon", "sorry",
        "sound", "sounds", "space", "speak", "special", "start", "state", "stay", "still", "stop",
        "story", "study", "stuff", "such", "super", "sure", "sweet", "take", "talk", "talking",
        "team", "tell", "telling", "text", "than", "thank", "thanks", "that", "the", "their",
        "them", "then", "there", "these", "they", "thing", "things", "think", "thinking", "this",
        "those", "though", "thought", "three", "through", "time", "to", "today", "together", "told",
        "tomorrow", "too", "took", "top", "total", "try", "trying", "turn", "two", "under",
        "understand", "until", "up", "update", "upon", "us", "use", "used", "using", "very",
        "video", "visit", "wait", "waiting", "walk", "want", "wanted", "was", "watch", "way",
        "we", "week", "weekend", "welcome", "well", "went", "were", "what", "whatever", "when",
        "where", "which", "while", "white", "who", "whole", "why", "will", "wish", "with",
        "within", "without", "won", "wonderful", "word", "words", "work", "working", "world", "worry",
        "would", "write", "wrong", "yeah", "year", "yes", "yesterday", "yet", "you", "young",
        "your", "yourself",
        // Common conversational transliterated words (Kannada / Hindi)
        "namaskara", "namaste", "hegiddira", "hegidiya", "oota", "banni", "beku", "beda", "illa",
        "houdu", "chennagiddini", "dhanyavada", "kannada", "kelsa", "samaya", "yaar", "kya", "hai",
        "haan", "theek", "shukriya", "bhai", "kaisa", "bolo", "kal", "aaj", "abhi"
    };

    static {
        Arrays.sort(VOCABULARY);
    }

    /**
     * Extracts the active word currently being typed right before the cursor.
     */
    public static String extractCurrentWord(InputConnection ic) {
        if (ic == null) return "";
        CharSequence textBefore = ic.getTextBeforeCursor(40, 0);
        if (textBefore == null || textBefore.length() == 0) return "";

        int i = textBefore.length() - 1;
        // Stop at whitespace or common punctuation
        while (i >= 0) {
            char c = textBefore.charAt(i);
            if (Character.isWhitespace(c) || c == '.' || c == ',' || c == '!' || c == '?' || c == ':' || c == ';') {
                break;
            }
            i--;
        }
        return textBefore.subSequence(i + 1, textBefore.length()).toString();
    }

    /**
     * Returns matching word suggestions for the prefix, retaining the case of the typed input.
     */
    public static List<String> getSuggestions(String inputPrefix, int maxResults) {
        if (inputPrefix == null || inputPrefix.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String prefix = inputPrefix.trim().toLowerCase();
        List<String> results = new ArrayList<>();

        // Binary search for closest prefix match
        int idx = Arrays.binarySearch(VOCABULARY, prefix);
        if (idx < 0) {
            idx = -(idx + 1);
        }

        // Exact match first if exists
        for (int i = idx; i < VOCABULARY.length && results.size() < maxResults; i++) {
            String word = VOCABULARY[i];
            if (word.startsWith(prefix)) {
                results.add(formatWordCase(word, inputPrefix));
            } else {
                break; // Because array is sorted, prefix matches are contiguous
            }
        }

        // If fewer results, search for contains as fallback
        if (results.size() < maxResults) {
            for (String word : VOCABULARY) {
                if (results.size() >= maxResults) break;
                if (!word.startsWith(prefix) && word.contains(prefix)) {
                    String formatted = formatWordCase(word, inputPrefix);
                    if (!results.contains(formatted)) {
                        results.add(formatted);
                    }
                }
            }
        }

        return results;
    }

    private static String formatWordCase(String word, String reference) {
        if (word == null || word.isEmpty()) return "";
        if (reference.length() == 0) return word;

        boolean allUpper = true;
        for (int i = 0; i < reference.length(); i++) {
            if (Character.isLetter(reference.charAt(i)) && !Character.isUpperCase(reference.charAt(i))) {
                allUpper = false;
                break;
            }
        }

        if (allUpper && reference.length() > 1) {
            return word.toUpperCase();
        } else if (Character.isUpperCase(reference.charAt(0))) {
            return Character.toUpperCase(word.charAt(0)) + (word.length() > 1 ? word.substring(1) : "");
        } else {
            return word.toLowerCase();
        }
    }
}
