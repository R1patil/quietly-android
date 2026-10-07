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

    private static volatile List<String> cachedEligibleModels = new ArrayList<>();

    public static void setCachedEligibleModels(List<String> models) {
        if (models != null && !models.isEmpty()) {
            cachedEligibleModels = new ArrayList<>(models);
        }
    }

    public static List<String> getCachedEligibleModels() {
        return cachedEligibleModels;
    }

    public interface ModelsCallback {
        void onSuccess(List<String> eligibleModels);
        void onError(String error);
    }

    public static String pickBestModel(List<String> eligibleList, String tone) {
        if (eligibleList == null || eligibleList.isEmpty()) {
            return "llama-3.3-70b-versatile";
        }
        boolean needsReasoning = tone != null && (tone.toLowerCase().contains("think")
                || tone.toLowerCase().contains("🧠")
                || tone.toLowerCase().contains("formal")
                || tone.toLowerCase().contains("💼"));

        if (needsReasoning) {
            for (String m : eligibleList) {
                String mLower = m.toLowerCase();
                if (mLower.contains("120b") || mLower.contains("70b") || mLower.contains("versatile") || mLower.contains("reason")) {
                    return m;
                }
            }
        }

        // Prefer fast chat models for instant typing responsiveness
        for (String m : eligibleList) {
            String mLower = m.toLowerCase();
            if (mLower.contains("8b") || mLower.contains("20b") || mLower.contains("mini") || mLower.contains("instant") || mLower.contains("turbo")) {
                return m;
            }
        }

        return eligibleList.get(0);
    }

    public static String resolveModel(String model, String tone, boolean hasDraft) {
        if (model == null || model.isEmpty() || "auto-smart".equalsIgnoreCase(model) || "auto".equalsIgnoreCase(model)) {
            if (!cachedEligibleModels.isEmpty()) {
                return pickBestModel(cachedEligibleModels, tone);
            }
            return "llama-3.3-70b-versatile";
        }

        // If the model is an outdated decommissioned identifier, gracefully fall back to best eligible model
        String mLower = model.toLowerCase();
        if (mLower.contains("deepseek-r1-distill") || mLower.contains("llama-3.1-8b-instant")) {
            if (!cachedEligibleModels.isEmpty()) {
                return pickBestModel(cachedEligibleModels, tone);
            }
            return "llama-3.3-70b-versatile";
        }

        return model.trim();
    }

    public void fetchEligibleModels(String apiKey, ModelsCallback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("No Groq API key configured"));
            return;
        }

        executor.execute(() -> {
            try {
                List<String> models = fetchEligibleModelsSync(apiKey);
                if (models != null && !models.isEmpty()) {
                    setCachedEligibleModels(models);
                    mainHandler.post(() -> callback.onSuccess(models));
                } else {
                    mainHandler.post(() -> callback.onError("No eligible chat models found on your Groq account"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Error fetching models"));
            }
        });
    }

    private List<String> fetchEligibleModelsSync(String apiKey) throws Exception {
        Request request = new Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .get()
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
                throw new Exception(errMsg);
            }

            JSONObject root = new JSONObject(respBody);
            JSONArray data = root.optJSONArray("data");
            List<String> eligible = new ArrayList<>();
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    boolean active = item.optBoolean("active", true);
                    if (!active) continue;
                    String id = item.optString("id", "").trim();
                    if (id.isEmpty()) continue;
                    String idLower = id.toLowerCase();
                    if (idLower.contains("whisper")
                            || idLower.contains("guard")
                            || idLower.contains("safeguard")
                            || idLower.contains("tts")
                            || idLower.contains("moderation")
                            || idLower.contains("embedding")) {
                        continue;
                    }
                    eligible.add(id);
                }
            }
            if (!eligible.isEmpty()) {
                setCachedEligibleModels(eligible);
            }
            return eligible;
        }
    }

    public void generateReplies(String apiKey, String model, String tone, String context, String draft, Callback callback) {
        generateReplies(apiKey, model, tone, context, draft, "auto", callback);
    }

    public void generateReplies(String apiKey, String model, String tone, String context, String draft, String language, Callback callback) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("No Groq API key configured"));
            return;
        }

        String resolved = resolveModel(model, tone, draft != null && !draft.trim().isEmpty());

        executor.execute(() -> {
            try {
                JSONObject payload = buildPayload(resolved, tone, context, draft, language);
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

                        // If model is decommissioned or not accessible, self-heal: fetch eligible models and retry with active model
                        String errLower = errMsg.toLowerCase();
                        boolean isModelIneligible = errLower.contains("does not exist")
                                || errLower.contains("not have access")
                                || errLower.contains("decommissioned")
                                || errLower.contains("no longer supported");

                        if (isModelIneligible) {
                            try {
                                List<String> fresh = fetchEligibleModelsSync(apiKey);
                                if (fresh != null && !fresh.isEmpty()) {
                                    String retryModel = pickBestModel(fresh, tone);
                                    if (!retryModel.equalsIgnoreCase(resolved)) {
                                        JSONObject retryPayload = buildPayload(retryModel, tone, context, draft, language);
                                        RequestBody retryBody = RequestBody.create(retryPayload.toString(), JSON_MEDIA_TYPE);
                                        Request retryReq = new Request.Builder()
                                                .url(GROQ_URL)
                                                .header("Authorization", "Bearer " + apiKey.trim())
                                                .header("Content-Type", "application/json")
                                                .post(retryBody)
                                                .build();
                                        try (Response retryResp = httpClient.newCall(retryReq).execute()) {
                                            String retryBodyStr = retryResp.body() != null ? retryResp.body().string() : "";
                                            if (retryResp.isSuccessful()) {
                                                List<String> replies = parseReplies(retryBodyStr);
                                                if (!replies.isEmpty()) {
                                                    mainHandler.post(() -> callback.onSuccess(replies));
                                                    return;
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

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

        String toneInstruction = "warm, friendly, and natural";
        if (tone != null && (tone.toLowerCase().contains("formal") || tone.toLowerCase().contains("💼"))) {
            toneInstruction = "polite, professional, and clear";
        } else if (tone != null && (tone.toLowerCase().contains("direct") || tone.toLowerCase().contains("⚡"))) {
            toneInstruction = "concise, direct, and under 10 words";
        } else if (tone != null && (tone.toLowerCase().contains("fix") || tone.toLowerCase().contains("✍️"))) {
            toneInstruction = "polished, well-phrased, and free of typos";
        } else if (tone != null && (tone.toLowerCase().contains("think") || tone.toLowerCase().contains("🧠"))) {
            toneInstruction = "sharp, analytical, calculating any math or decision logic carefully";
        }

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
            // Clean DeepSeek-R1 chain-of-thought tags if present
            content = content.replaceAll("(?s)<think>.*?</think>", "").trim();

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

        String chosenModel = resolveModel(model, "smart", false);

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
