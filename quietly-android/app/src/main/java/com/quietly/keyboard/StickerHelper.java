package com.quietly.keyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StickerHelper {

    private static final String TAG = "StickerHelper";
    private static final String STICKER_DIR = "stickers";
    private static final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();

    public static final String[] CATEGORY_NAMES = {"🔥 Reactions", "😂 Mood", "❤️ Love"};
    public static final String[] CATEGORY_ICONS = {"🔥", "😂", "❤️"};

    private static final List<List<StickerItem>> CATEGORIES = new ArrayList<>();

    static {
        // Category 0: Reactions 🔥
        List<StickerItem> reactions = new ArrayList<>();
        reactions.add(new StickerItem("react_fire", "🔥", "LIT!", "That is on fire!", 0xFFFF5722));
        reactions.add(new StickerItem("react_100", "💯", "FACTS!", "100% agreed!", 0xFFE91E63));
        reactions.add(new StickerItem("react_thumbs", "👍", "APPROVED", "Looks good to me", 0xFF4CAF50));
        reactions.add(new StickerItem("react_clap", "👏", "BRAVO!", "Well done!", 0xFFFF9800));
        reactions.add(new StickerItem("react_mindblown", "🤯", "MIND BLOWN", "Unbelievable!", 0xFF00BCD4));
        reactions.add(new StickerItem("react_cool", "😎", "TOO COOL", "Like a boss", 0xFF2196F3));
        reactions.add(new StickerItem("react_salute", "🫡", "RESPECT", "Salute to that", 0xFF607D8B));
        reactions.add(new StickerItem("react_rocket", "🚀", "TO THE MOON", "Let's go!", 0xFFFF9800));
        reactions.add(new StickerItem("react_strong", "💪", "STAY STRONG", "Keep pushing!", 0xFFE65100));
        reactions.add(new StickerItem("react_magic", "✨", "MAGIC", "Pure brilliance", 0xFFFFD700));
        reactions.add(new StickerItem("react_pray", "🙌", "BLESSED", "Praise that", 0xFF9C27B0));
        reactions.add(new StickerItem("react_peace", "✌️", "PEACE", "Good vibes only", 0xFF009688));

        // Category 1: Mood & Fun 😂
        List<StickerItem> mood = new ArrayList<>();
        mood.add(new StickerItem("mood_laugh", "😂", "DEAD!", "Crying with laughter", 0xFFFFC107));
        mood.add(new StickerItem("mood_cry", "😭", "CAN'T EVEN", "Tears of joy", 0xFF03A9F4));
        mood.add(new StickerItem("mood_party", "🥳", "CELEBRATE!", "Time to celebrate!", 0xFF9C27B0));
        mood.add(new StickerItem("mood_plead", "🥺", "PLEASE", "Pretty please?", 0xFFFF80AB));
        mood.add(new StickerItem("mood_sleep", "😴", "GOOD NIGHT", "Logging off to sleep", 0xFF3F51B5));
        mood.add(new StickerItem("mood_think", "🤔", "HMM...", "Thinking about it", 0xFF795548));
        mood.add(new StickerItem("mood_shh", "🤫", "SECRET", "Keep it on the low", 0xFF009688));
        mood.add(new StickerItem("mood_coffee", "☕", "NEED COFFEE", "Fueling up", 0xFF8D6E63));
        mood.add(new StickerItem("mood_game", "🎮", "GAME ON!", "In gamer mode", 0xFF673AB7));
        mood.add(new StickerItem("mood_pizza", "🍕", "FOOD TIME", "Craving a bite", 0xFFFF5722));
        mood.add(new StickerItem("mood_bday", "🎂", "HAPPY BDAY!", "Wishing happy birthday", 0xFFE91E63));
        mood.add(new StickerItem("mood_wink", "😜", "JUST KIDDING", "Haha just joking", 0xFFFFB300));

        // Category 2: Love & Care ❤️
        List<StickerItem> love = new ArrayList<>();
        love.add(new StickerItem("love_heart", "❤️", "MUCH LOVE", "Sent with love", 0xFFE91E63));
        love.add(new StickerItem("love_sweet", "🥰", "SO SWEET", "Heart melted", 0xFFFF4081));
        love.add(new StickerItem("love_spark", "💖", "SPECIAL", "You are special", 0xFFFF1744));
        love.add(new StickerItem("love_thanks", "🙏", "THANK YOU", "Grateful & thank you", 0xFFFF9800));
        love.add(new StickerItem("love_handshake", "🤝", "DEAL DONE", "Pleasure doing business", 0xFF4CAF50));
        love.add(new StickerItem("love_star", "🌟", "SUPERSTAR", "You're a rockstar", 0xFFFFC107));
        love.add(new StickerItem("love_crown", "👑", "ROYAL VIBES", "King / Queen energy", 0xFFFFB300));
        love.add(new StickerItem("love_trophy", "🏆", "CHAMPION!", "Number one always", 0xFFFF9800));
        love.add(new StickerItem("love_letter", "💌", "FOR YOU", "Thinking of you", 0xFFF06292));
        love.add(new StickerItem("love_diamond", "💎", "PRICELESS", "You are a gem", 0xFF00E5FF));
        love.add(new StickerItem("love_sun", "☀️", "SUNSHINE", "Bright day ahead", 0xFFFFC107));
        love.add(new StickerItem("love_hug", "🤗", "WARM HUG", "Sending virtual hug", 0xFFAB47BC));

        CATEGORIES.add(reactions);
        CATEGORIES.add(mood);
        CATEGORIES.add(love);
    }

    public static List<StickerItem> getStickersForCategory(int categoryIndex) {
        if (categoryIndex >= 0 && categoryIndex < CATEGORIES.size()) {
            return CATEGORIES.get(categoryIndex);
        }
        return CATEGORIES.get(0);
    }

    public static int getCategoryCount() {
        return CATEGORIES.size();
    }

    /**
     * Preloads all stickers in the background so they are generated into PNGs
     * before the user opens the tab.
     */
    public static void preloadStickers(final Context context) {
        backgroundExecutor.execute(() -> {
            try {
                for (List<StickerItem> cat : CATEGORIES) {
                    for (StickerItem item : cat) {
                        getOrGenerateStickerFile(context, item);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error preloading stickers", e);
            }
        });
    }

    /**
     * Retrieves or generates an official WhatsApp/Telegram compatible 512x512 PNG sticker.
     */
    public static File getOrGenerateStickerFile(Context context, StickerItem item) {
        File stickerDir = new File(context.getCacheDir(), STICKER_DIR);
        if (!stickerDir.exists()) {
            stickerDir.mkdirs();
        }

        File file = new File(stickerDir, item.getId() + ".png");
        if (file.exists() && file.length() > 0) {
            return file;
        }

        // Generate high resolution 512x512 transparent sticker with crisp designer badge
        Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 1. Transparent background
        canvas.drawColor(Color.TRANSPARENT);

        // 2. Rounded bubble container (WhatsApp sticker 512x512 with safe margin)
        RectF bubbleRect = new RectF(28, 28, 484, 484);
        Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bubblePaint.setStyle(Paint.Style.FILL);
        bubblePaint.setColor(0xEE1E2430); // Deep sleek dark glass background
        canvas.drawRoundRect(bubbleRect, 68, 68, bubblePaint);

        // 3. Vibrant border matching item accent color
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(8f);
        borderPaint.setColor(item.getAccentColor());
        canvas.drawRoundRect(bubbleRect, 68, 68, borderPaint);

        // 4. Draw large central Emoji
        Paint emojiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        emojiPaint.setTextSize(185f);
        emojiPaint.setTextAlign(Paint.Align.CENTER);
        // Position emoji vertically in upper half
        canvas.drawText(item.getEmoji(), 256, 260, emojiPaint);

        // 5. Draw sleek bottom banner pill
        RectF pillRect = new RectF(64, 370, 448, 442);
        Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pillPaint.setStyle(Paint.Style.FILL);
        pillPaint.setColor(item.getAccentColor());
        canvas.drawRoundRect(pillRect, 32, 32, pillPaint);

        // 6. Draw Title in banner
        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(34f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextAlign(Paint.Align.CENTER);

        // Center vertically in banner pill
        Rect textBounds = new Rect();
        titlePaint.getTextBounds(item.getTitle(), 0, item.getTitle().length(), textBounds);
        float textY = pillRect.centerY() + (textBounds.height() / 2f);
        canvas.drawText(item.getTitle(), pillRect.centerX(), textY, titlePaint);

        // Save to PNG file
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save sticker PNG: " + item.getId(), e);
        } finally {
            bitmap.recycle();
        }

        return file;
    }
}
