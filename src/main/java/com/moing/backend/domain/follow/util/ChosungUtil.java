package com.moing.backend.domain.follow.util;

public class ChosungUtil {

    private static final char[] CHOSUNG = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ',
            'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private static final int KOREAN_BASE = 0xAC00;
    private static final int SYLLABLE_PER_CHOSUNG = 21 * 28; // 588

    public static boolean hasChosung(String str) {
        for (char c : str.toCharArray()) {
            if (isChosung(c)) return true;
        }
        return false;
    }

    public static String toRegexPattern(String query) {
        StringBuilder sb = new StringBuilder();
        for (char c : query.toCharArray()) {
            if (isChosung(c)) {
                int index = getChosungIndex(c);
                char start = (char) (KOREAN_BASE + index * SYLLABLE_PER_CHOSUNG);
                char end = (char) (start + SYLLABLE_PER_CHOSUNG - 1);
                sb.append('[').append(start).append('-').append(end).append(']');
            } else {
                sb.append(escapeRegex(c));
            }
        }
        return sb.toString();
    }

    private static boolean isChosung(char c) {
        for (char ch : CHOSUNG) {
            if (ch == c) return true;
        }
        return false;
    }

    private static int getChosungIndex(char c) {
        for (int i = 0; i < CHOSUNG.length; i++) {
            if (CHOSUNG[i] == c) return i;
        }
        return -1;
    }

    private static String escapeRegex(char c) {
        String special = "\\[](){}.*+?^$|";
        if (special.indexOf(c) >= 0) {
            return "\\" + c;
        }
        return String.valueOf(c);
    }
}
