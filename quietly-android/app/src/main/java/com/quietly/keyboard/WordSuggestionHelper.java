package com.quietly.keyboard;

import android.content.Context;
import android.view.inputmethod.InputConnection;

import java.util.Collections;
import java.util.List;

/**
 * Helper class that provides word suggestions powered by a Trie and
 * integrates with Android's system UserDictionary.
 */
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

    private static final Trie trie = new Trie();
    private static boolean initialized = false;

    static {
        // Populate base vocabulary into the Trie with default frequency
        for (String word : VOCABULARY) {
            trie.insert(word, 10);
        }
    }

    /**
     * Initializes the Trie with words from Android UserDictionary and local storage.
     */
    public static synchronized void init(Context context) {
        if (initialized || context == null) return;
        initialized = true;
        UserDictionaryHelper.loadIntoTrie(context.getApplicationContext(), trie);
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
     * Returns matching word suggestions for the prefix using the Trie with frequency ranking.
     */
    public static List<String> getSuggestions(String inputPrefix, int maxResults) {
        if (inputPrefix == null || inputPrefix.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return trie.getSuggestions(inputPrefix, maxResults);
    }

    /**
     * Learns a newly completed word into the Trie and Android UserDictionary.
     */
    public static void learnWord(Context context, String word) {
        if (word == null || word.trim().isEmpty()) return;
        UserDictionaryHelper.learnWord(context, trie, word);
    }
}
