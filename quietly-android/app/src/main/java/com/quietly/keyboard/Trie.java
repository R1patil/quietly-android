package com.quietly.keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * High-performance Prefix Tree (Trie) for autocomplete with frequency-based ranking.
 * Supports dynamic insertions, case preservation, and top-K suggestion retrieval.
 */
public class Trie {

    public static class TrieNode {
        // Map allows support for letters, apostrophes (e.g. don't), and Unicode
        final Map<Character, TrieNode> children = new HashMap<>(4);
        boolean isEndOfWord = false;
        int frequency = 0;
    }

    private static class WordCandidate implements Comparable<WordCandidate> {
        final String word;
        final int frequency;

        WordCandidate(String word, int frequency) {
            this.word = word;
            this.frequency = frequency;
        }

        @Override
        public int compareTo(WordCandidate other) {
            // Min-heap: lowest frequency at head
            return Integer.compare(this.frequency, other.frequency);
        }
    }

    private final TrieNode root = new TrieNode();

    public TrieNode getRoot() {
        return root;
    }

    /**
     * Inserts a word with a starting frequency score into the Trie.
     */
    public synchronized void insert(String word, int frequency) {
        if (word == null || word.trim().isEmpty()) return;
        String cleanWord = word.trim().toLowerCase();

        TrieNode current = root;
        for (int i = 0; i < cleanWord.length(); i++) {
            char ch = cleanWord.charAt(i);
            TrieNode child = current.children.get(ch);
            if (child == null) {
                child = new TrieNode();
                current.children.put(ch, child);
            }
            current = child;
        }

        current.isEndOfWord = true;
        // Keep the higher frequency if word was already present
        if (frequency > current.frequency) {
            current.frequency = frequency;
        } else if (current.frequency == 0) {
            current.frequency = Math.max(1, frequency);
        }
    }

    /**
     * Learns a typed word by incrementing its frequency score.
     */
    public synchronized void learnWord(String word) {
        if (word == null || word.trim().isEmpty()) return;
        String cleanWord = word.trim().toLowerCase();

        TrieNode current = root;
        for (int i = 0; i < cleanWord.length(); i++) {
            char ch = cleanWord.charAt(i);
            TrieNode child = current.children.get(ch);
            if (child == null) {
                child = new TrieNode();
                current.children.put(ch, child);
            }
            current = child;
        }

        current.isEndOfWord = true;
        current.frequency += 5; // Boost frequency for user typing habit
    }

    /**
     * Returns top-K suggestions matching the given prefix, ranked by frequency.
     */
    public synchronized List<String> getSuggestions(String prefix, int maxResults) {
        if (prefix == null || prefix.trim().isEmpty() || maxResults <= 0) {
            return Collections.emptyList();
        }

        String lowerPrefix = prefix.trim().toLowerCase();
        TrieNode current = root;

        // Step 1: Traverse down the prefix path
        for (int i = 0; i < lowerPrefix.length(); i++) {
            char ch = lowerPrefix.charAt(i);
            current = current.children.get(ch);
            if (current == null) {
                return Collections.emptyList(); // Prefix not found
            }
        }

        // Step 2: Use Min-Heap to collect top-K candidate words
        PriorityQueue<WordCandidate> minHeap = new PriorityQueue<>(maxResults);
        collectWords(current, new StringBuilder(lowerPrefix), minHeap, maxResults);

        // Step 3: Extract from heap in descending order (highest frequency first)
        List<WordCandidate> topCandidates = new ArrayList<>();
        while (!minHeap.isEmpty()) {
            topCandidates.add(minHeap.poll());
        }
        Collections.reverse(topCandidates);

        // Step 4: Format word casing matching user input style
        List<String> results = new ArrayList<>(topCandidates.size());
        for (WordCandidate candidate : topCandidates) {
            results.add(formatWordCase(candidate.word, prefix));
        }

        return results;
    }

    private void collectWords(TrieNode node, StringBuilder path, PriorityQueue<WordCandidate> heap, int maxResults) {
        if (node.isEndOfWord) {
            heap.offer(new WordCandidate(path.toString(), node.frequency));
            if (heap.size() > maxResults) {
                heap.poll(); // Evict lowest frequency candidate
            }
        }

        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            path.append(entry.getKey());
            collectWords(entry.getValue(), path, heap, maxResults);
            path.deleteCharAt(path.length() - 1); // Backtrack
        }
    }

    private String formatWordCase(String word, String reference) {
        if (word == null || word.isEmpty()) return "";
        if (reference == null || reference.isEmpty()) return word;

        boolean allUpper = true;
        for (int i = 0; i < reference.length(); i++) {
            if (Character.isLetter(reference.charAt(i)) && !Character.isUpperCase(reference.charAt(i))) {
                allUpper = false;
                break;
            }
        }
        if (allUpper && reference.length() > 1) {
            return word.toUpperCase();
        }

        if (Character.isUpperCase(reference.charAt(0))) {
            return Character.toUpperCase(word.charAt(0)) + (word.length() > 1 ? word.substring(1) : "");
        }

        return word;
    }
}
