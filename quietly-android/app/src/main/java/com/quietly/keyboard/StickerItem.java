package com.quietly.keyboard;

public class StickerItem {
    private final String id;
    private final String emoji;
    private final String title;
    private final String caption;
    private final int accentColor;

    public StickerItem(String id, String emoji, String title, String caption, int accentColor) {
        this.id = id;
        this.emoji = emoji;
        this.title = title;
        this.caption = caption;
        this.accentColor = accentColor;
    }

    public String getId() {
        return id;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getTitle() {
        return title;
    }

    public String getCaption() {
        return caption;
    }

    public int getAccentColor() {
        return accentColor;
    }

    public String getFallbackText() {
        return emoji + " " + caption;
    }
}
