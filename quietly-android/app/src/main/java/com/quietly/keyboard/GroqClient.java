package com.quietly.keyboard;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GroqClient {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface Callback {
        void onSuccess(List<String> suggestions);
        void onError(String error);
    }

    public GroqClient() {
        this.httpClient = new OkHttpClient.Builder().build();
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void generateReplies(String apiKey, String model, String tone, String context, String draft, Callback callback) {
        generateReplies(apiKey, model, tone, context, draft, "auto", callback);
    }

    public void generateReplies(String apiKey, String model, String tone, String context, String draft, String language, Callback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("No Groq API key configured"));
            return;
        }

        String chosenModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "llama-3.3-70b-versatile";

        executor.execute(() -> {
            try {
                JSONObject payload = buildPayload(chosenModel, tone, context, draft, language);
                RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);

                Request request = new Request.Builder()
                        .url(GROQ_URL)
                        .header("Authorization", "Bearer " + apiKey.trim())
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        String errMsg = "Groq HTTP " + response.code();
                        try {
                            JSONObject errJson = new JSONObject(respBody);
                            if (errJson.has("error")) {
                                errMsg = errJson.getJSONObject("error").optString("message", errMsg);
                            }
                        } catch (Exception ignored) {}
                        final String finalErr = errMsg;
                        mainHandler.post(() -> callback.onError(finalErr));
                        return;
                    }

                    List<String> replies = parseReplies(respBody);
                    if (replies.isEmpty()) {
                        mainHandler.post(() -> callback.onError("No suggestions returned"));
                    } else {
                        mainHandler.post(() -> callback.onSuccess(replies));
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Network error"));
            }
        });
    }

    private JSONObject buildPayload(String model, String tone, String context, String draft, String language) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("temperature", 0.4);
        payload.put("max_tokens", 350);

        JSONObject responseFormat = new JSONObject();
        responseFormat.put("type", "json_object");
        payload.put("response_format", responseFormat);

        JSONArray messages = new JSONArray();

        // System prompt
        String toneInstruction = "warm, friendly, and natural";
        if ("Formal".equalsIgnoreCase(tone)) toneInstruction = "polite, professional, and clear";
        else if ("Direct".equalsIgnoreCase(tone)) toneInstruction = "concise, direct, and under 10 words";
        else if ("Fix".equalsIgnoreCase(tone)) toneInstruction = "polished, well-phrased, and free of typos";

        String langInstruction = "Reply in the same language and script as the chat context (supports English, Hindi, Hinglish, Kannada).";
        if ("hi".equalsIgnoreCase(language)) {
            langInstruction = "Generate replies in Hindi or Hinglish matching the conversation.";
        } else if ("kn".equalsIgnoreCase(language)) {
            langInstruction = "Generate replies in Kannada (or Kannada conversational phrasing).";
        } else if ("en".equalsIgnoreCase(language)) {
            langInstruction = "Generate replies in English.";
        }

        boolean hasDraft = (draft != null && !draft.trim().isEmpty());

        String systemPrompt;
        if (hasDraft) {
            systemPrompt = "You polish, rephrase, and improve the user's typed draft message for WhatsApp.\n"
                    + "Tone: " + toneInstruction + ".\n"
                    + "Language rule: " + langInstruction + "\n"
                    + "All 3 options must be polished variants of the user's draft intent, preserving their meaning while making it clearer, fluent, and well-written.\n"
                    + "Return JSON with exactly 3 varied polished options: {\"r1\":{\"text\":\"...\"},\"r2\":{\"text\":\"...\"},\"r3\":{\"text\":\"...\"}}.\n"
                    + "Do not include quotes or conversational filler. Keep each suggestion under 20 words.";
        } else {
            systemPrompt = "You suggest intelligent WhatsApp message replies directly addressing the ongoing chat context.\n"
                    + "Tone: " + toneInstruction + ".\n"
                    + "Language rule: " + langInstruction + "\n"
                    + "Return JSON with exactly 3 varied reply options: {\"r1\":{\"text\":\"...\"},\"r2\":{\"text\":\"...\"},\"r3\":{\"text\":\"...\"}}.\n"
                    + "Do not include quotes or conversational filler. Keep each suggestion under 15 words.";
        }

        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.put(sysMsg);

        // User prompt
        StringBuilder userContent = new StringBuilder();
        if (hasDraft) {
            userContent.append("MY DRAFT: \"").append(draft.trim()).append("\"\n");
            if (context != null && !context.trim().isEmpty()) {
                userContent.append("Conversation Context: \"").append(context.trim()).append("\"\n");
            }
            userContent.append("Write 3 polished, improved variants of MY DRAFT (Option 1: natural & fluent, Option 2: clear & direct, Option 3: polite & well-crafted).");
        } else if (context != null && !context.trim().isEmpty()) {
            userContent.append("Active Chat Context: \"").append(context.trim()).append("\"\n");
            userContent.append("Suggest 3 intelligent, highly context-relevant replies directly responding to the above chat.");
        } else {
            userContent.append("Suggest 3 common friendly conversation starters or quick check-ins.");
        }

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userContent.toString());
        messages.put(userMsg);

        payload.put("messages", messages);
        return payload;
    }

    private List<String> parseReplies(String jsonResponse) {
        List<String> results = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonResponse);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return results;

            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.optJSONObject("message");
            if (message == null) return results;

            String content = message.optString("content", "").trim();

            // Extract JSON from content
            JSONObject parsed = null;
            try {
                parsed = new JSONObject(content);
            } catch (Exception e) {
                Matcher m = Pattern.compile("\\{[\\s\\S]*\\}").matcher(content);
                if (m.find()) {
                    parsed = new JSONObject(m.group(0));
                }
            }

            if (parsed != null) {
                for (String key : new String[]{"r1", "r2", "r3"}) {
                    if (parsed.has(key)) {
                        Object val = parsed.get(key);
                        String text = "";
                        if (val instanceof JSONObject) {
                            text = ((JSONObject) val).optString("text", "");
                        } else if (val instanceof String) {
                            text = (String) val;
                        }
                        text = text.trim();
                        if (!text.isEmpty() && !results.contains(text)) {
                            results.add(text);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return results;
    }

    public void triageVideos(String apiKey, String model, JSONArray videos, Callback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("No Groq API key configured"));
            return;
        }

        String chosenModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "qwen/qwen3.8-27b";

        executor.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("model", chosenModel);
                payload.put("temperature", 0.0);
                payload.put("max_tokens", 500);

                JSONObject responseFormat = new JSONObject();
                responseFormat.put("type", "json_object");
                payload.put("response_format", responseFormat);

                JSONArray messages = new JSONArray();

                String systemPrompt = "You are an aggressive academic YouTube feed filter.\n"
                        + "DEFAULT ACTION: HIDE EVERYTHING. Only keep a video if it is explicitly a serious educational course, technical tutorial, or academic lecture.\n"
                        + "Keep ONLY: coding, programming, software engineering, computer science, mathematics, academic lectures, science documentaries, finance education, skill building.\n"
                        + "Aggressively HIDE: pranks, comedy, jokes, standup, sketches, reactions, gaming, memes, vlogs, gossip, music videos, movie clips, ASMR, clickbait, podcasts without educational focus.\n"
                        + "Zero tolerance for comedy, humor, or entertainment. Even if educational in part, if it is presented as comedy or entertainment, HIDE IT.\n"
                        + "When in doubt, HIDE IT. An empty list is completely fine.\n"
                        + "Return JSON: {\"keep\":[\"v1\",\"v4\",...]}. If none qualify, return {\"keep\":[]}.";

                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemPrompt);
                messages.put(sysMsg);

                StringBuilder userContent = new StringBuilder();
                userContent.append("Which of these videos should I keep?\n");
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject v = videos.getJSONObject(i);
                    userContent.append("v").append(i + 1).append(" ")
                            .append(v.optString("title", ""))
                            .append(" | ")
                            .append(v.optString("channel", "unknown"))
                            .append("\n");
                }

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userContent.toString());
                messages.put(userMsg);

                payload.put("messages", messages);

                RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);
                Request request = new Request.Builder()
                        .url(GROQ_URL)
                        .header("Authorization", "Bearer " + apiKey.trim())
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        mainHandler.post(() -> callback.onError("Groq triage error: " + response.code()));
                        return;
                    }

                    List<String> keptIds = parseKeptIds(respBody, videos);
                    mainHandler.post(() -> callback.onSuccess(keptIds));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Triage error"));
            }
        });
    }

    private List<String> parseKeptIds(String jsonResponse, JSONArray originalVideos) {
        List<String> keptVideoIds = new ArrayList<>();
        try {
            JSONObject root = new JSONObject(jsonResponse);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return keptVideoIds;

            String content = choices.getJSONObject(0).optJSONObject("message").optString("content", "");
            JSONObject parsed = null;
            try {
                parsed = new JSONObject(content);
            } catch (Exception e) {
                Matcher m = Pattern.compile("\\{[\\s\\S]*\\}").matcher(content);
                if (m.find()) parsed = new JSONObject(m.group(0));
            }

            if (parsed != null && parsed.has("keep")) {
                JSONArray keepArr = parsed.getJSONArray("keep");
                List<String> keptCodes = new ArrayList<>();
                for (int i = 0; i < keepArr.length(); i++) {
                    keptCodes.add(keepArr.getString(i).trim());
                }

                for (int i = 0; i < originalVideos.length(); i++) {
                    String code = "v" + (i + 1);
                    if (keptCodes.contains(code)) {
                        keptVideoIds.add(originalVideos.getJSONObject(i).optString("id", ""));
                    }
                }
            }
        } catch (Exception ignored) {}
        return keptVideoIds;
    }
}
