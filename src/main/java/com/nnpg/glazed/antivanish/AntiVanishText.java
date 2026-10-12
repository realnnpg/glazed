package com.nnpg.glazed.antivanish;

import java.text.Normalizer;
import java.util.Locale;

public final class AntiVanishText {
    private AntiVanishText() {
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) return "";

        String decomposed = Normalizer.normalize(stripLegacyCodes(text), Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(decomposed.length());
        boolean spaced = true;

        int offset = 0;
        while (offset < decomposed.length()) {
            int codePoint = decomposed.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isMark(codePoint) || isFormatCode(codePoint)) continue;

            int folded = foldConfusable(codePoint);

            if (Character.isWhitespace(folded)) {
                if (!spaced) {
                    out.append(' ');
                    spaced = true;
                }
                continue;
            }

            if ((folded < 97 || folded > 122) && (folded < 48 || folded > 57)) continue;

            out.appendCodePoint(folded);
            spaced = false;
        }

        int length = out.length();
        if (length > 0 && out.charAt(length - 1) == ' ') out.setLength(length - 1);

        return out.toString();
    }

    public static boolean containsPlayerName(String displayedName, String playerName) {
        String rawDisplay = stripLegacyCodes(displayedName == null ? "" : displayedName).toLowerCase(Locale.ROOT);
        String rawPlayer = stripLegacyCodes(playerName == null ? "" : playerName).trim().toLowerCase(Locale.ROOT);

        if (rawPlayer.matches("[a-z0-9_]{1,16}")) {
            int from = 0;
            while (from <= rawDisplay.length() - rawPlayer.length()) {
                int match = rawDisplay.indexOf(rawPlayer, from);
                if (match < 0) return false;

                int end = match + rawPlayer.length();
                boolean leftBoundary = match == 0 || !isUsernameCharacter(rawDisplay.charAt(match - 1));
                boolean rightBoundary = end == rawDisplay.length() || !isUsernameCharacter(rawDisplay.charAt(end));

                if (leftBoundary && rightBoundary) return true;

                from = match + 1;
            }
            return false;
        }

        String displayed = normalize(displayedName);
        String player = normalize(playerName);
        if (displayed.isBlank() || player.isBlank()) return false;

        return displayed.equals(player) || (" " + displayed + " ").contains(" " + player + " ");
    }

    public static boolean looksLikeLeaveMessage(String message, String playerName) {
        if (!containsPlayerName(message, playerName)) return false;

        String text = " " + normalize(message) + " ";
        String player = normalize(playerName);
        if (player.isBlank()) return false;

        String prefix = " " + player + " ";

        return text.contains(prefix + "left the game ")
                || text.contains(prefix + "left ")
                || text.contains(prefix + "has left ")
                || text.contains(prefix + "quit ")
                || text.contains(prefix + "has quit ")
                || text.contains(prefix + "disconnected ")
                || text.contains(prefix + "logged out ");
    }

    public static boolean isPlausiblePlayerName(String name) {
        if (name == null) return false;

        String trimmed = name.trim();
        int len = trimmed.length();
        if (len < 3 || len > 16) return false;

        return trimmed.indexOf(' ') < 0;
    }

    public static boolean isUsername(String name) {
        if (name == null) return false;

        int len = name.length();
        if (len < 2 || len > 32) return false;

        boolean hasAlnum = false;
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            boolean alnum = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');

            if (alnum) {
                hasAlnum = true;
                continue;
            }

            if (c == '_' || c == '.' || c == ' ') continue;

            return false;
        }

        return hasAlnum;
    }

    private static boolean isUsernameCharacter(char character) {
        return (character >= 'a' && character <= 'z') || (character >= '0' && character <= '9') || character == '_';
    }

    private static boolean isMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK;
    }

    private static boolean isFormatCode(int codePoint) {
        return codePoint == 0xA7 || codePoint == 0x200B || codePoint == 0x200C || codePoint == 0x200D || codePoint == 0xFEFF;
    }

    public static String stripLegacyCodes(String text) {
        if (text == null || text.isEmpty()) return "";

        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                if ("0123456789abcdefklmnorx".indexOf(code) >= 0) {
                    i++;
                    continue;
                }
            }

            out.append(c);
        }

        return out.toString();
    }

    private static int foldConfusable(int codePoint) {
        return switch (codePoint) {
            case 593, 945, 1072, 7424 -> 97;
            case 665, 946, 1074 -> 98;
            case 1010, 1089, 7428 -> 99;
            case 1281, 7429 -> 100;
            case 949, 1077, 7431 -> 101;
            case 42800 -> 102;
            case 609, 610 -> 103;
            case 668, 1085 -> 104;
            case 618, 953, 1110 -> 105;
            case 1112, 7434 -> 106;
            case 954, 1082, 7435 -> 107;
            case 671 -> 108;
            case 1084, 7437 -> 109;
            case 628, 1400 -> 110;
            case 959, 1086, 7439 -> 111;
            case 961, 1088, 7448 -> 112;
            case 1307 -> 113;
            case 640 -> 114;
            case 1109, 42801 -> 115;
            case 964, 1090, 7451 -> 116;
            case 965, 7452 -> 117;
            case 957, 7456 -> 118;
            case 1121, 7457 -> 119;
            case 1093 -> 120;
            case 655, 1091 -> 121;
            case 7458, 7459 -> 122;
            default -> codePoint;
        };
    }
}
