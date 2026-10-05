package com.quietly.keyboard;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Toast;

import java.util.ArrayList;

public class VoiceInputHelper {

    public interface VoiceCallback {
        void onSpeechText(String text, boolean isFinal);
        void onListeningStateChanged(boolean isListening);
        void onError(String message);
    }

    private final Context context;
    private final VoiceCallback callback;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    public VoiceInputHelper(Context context, VoiceCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public boolean isListening() {
        return isListening;
    }

    public void startListening(String languageLocale) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onError("Speech recognition not available on this device");
            return;
        }

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer.destroy();
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
                speechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle params) {
                        isListening = true;
                        callback.onListeningStateChanged(true);
                    }

                    @Override
                    public void onBeginningOfSpeech() {}

                    @Override
                    public void onRmsChanged(float rmsdB) {}

                    @Override
                    public void onBufferReceived(byte[] buffer) {}

                    @Override
                    public void onEndOfSpeech() {
                        isListening = false;
                        callback.onListeningStateChanged(false);
                    }

                    @Override
                    public void onError(int error) {
                        isListening = false;
                        callback.onListeningStateChanged(false);
                        String msg = "Voice error: " + error;
                        if (error == SpeechRecognizer.ERROR_NO_MATCH) msg = "No speech detected";
                        else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) msg = "Mic permission required";
                        callback.onError(msg);
                    }

                    @Override
                    public void onResults(Bundle results) {
                        isListening = false;
                        callback.onListeningStateChanged(false);
                        if (results != null) {
                            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            if (matches != null && !matches.isEmpty()) {
                                callback.onSpeechText(matches.get(0), true);
                            }
                        }
                    }

                    @Override
                    public void onPartialResults(Bundle partialResults) {
                        if (partialResults != null) {
                            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            if (matches != null && !matches.isEmpty()) {
                                callback.onSpeechText(matches.get(0), false);
                            }
                        }
                    }

                    @Override
                    public void onEvent(int eventType, Bundle params) {}
                });

                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
                if (languageLocale != null && !languageLocale.isEmpty()) {
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale);
                }

                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                isListening = false;
                callback.onListeningStateChanged(false);
                callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to start voice recognition");
            }
        });
    }

    public void stopListening() {
        if (speechRecognizer != null && isListening) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    speechRecognizer.stopListening();
                } catch (Exception ignored) {}
                isListening = false;
                callback.onListeningStateChanged(false);
            });
        }
    }

    public void destroy() {
        if (speechRecognizer != null) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    speechRecognizer.destroy();
                } catch (Exception ignored) {}
                speechRecognizer = null;
                isListening = false;
            });
        }
    }
}
