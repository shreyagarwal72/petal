package com.petal.browser.unit;

/**
 * ReadingTimeEstimator calculates estimated reading duration and total word count for articles.
 */
public class ReadingTimeEstimator {

    public static class ReadingStats {
        public int wordCount;
        public int minutesRead;

        public ReadingStats(int wordCount, int minutesRead) {
            this.wordCount = wordCount;
            this.minutesRead = minutesRead;
        }
    }

    /**
     * Estimates reading time assuming average reading speed of 200 words per minute.
     */
    public static ReadingStats calculateReadingStats(String textBody) {
        if (textBody == null || textBody.trim().isEmpty()) return new ReadingStats(0, 0);

        String cleanText = textBody.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        if (cleanText.isEmpty()) return new ReadingStats(0, 0);

        String[] words = cleanText.split(" ");
        int wordCount = words.length;
        int minutes = (int) Math.ceil((double) wordCount / 200.0);

        return new ReadingStats(wordCount, Math.max(1, minutes));
    }
}
