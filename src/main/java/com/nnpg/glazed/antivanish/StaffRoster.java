package com.nnpg.glazed.antivanish;

import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class StaffRoster {
    public static final List<String> DEFAULT_NAMES = List.of(
            "FluffyMaster07", "archivePedro", "Munkerlich", "Frenk_Btw", "Napooo_", "auzzitech",
            "CryptoDaveYT", "W1zoX_", "ZEEF69", "Showered", "CaptainMoose35", "BobIsFound",
            "NoahvdAa", "0GSummer", "LzouzMP5", "PastaGamer08", "u_vv", "Owen1212055",
            "Splaterd", "Fallerfly", "Frwost", "bautiedgar", "Itszdeath", "DrDonutt"
    );

    private static final Set<Integer> STARS = new HashSet<>();

    static {
        "\u2605\u2606\u2726\u2727\u272A\u272F\u2730\u2B50".codePoints().forEach(STARS::add);
    }

    private final Set<String> names = new HashSet<>();
    private final Set<String> starred = new HashSet<>();
    private boolean starsEnabled = true;

    public void setNames(Collection<String> values) {
        names.clear();
        if (values == null) return;

        for (String value : values) {
            if (value != null && !value.isBlank()) names.add(value.trim().toLowerCase(Locale.ROOT));
        }
    }

    public void setStarsEnabled(boolean enabled) {
        starsEnabled = enabled;
    }

    public void learnFromTab(String name, Component display) {
        if (name == null || display == null) return;

        if (hasLeadingStar(display.getString())) starred.add(name.toLowerCase(Locale.ROOT));
    }

    public boolean isStaffName(String name) {
        if (name == null) return false;

        String key = name.toLowerCase(Locale.ROOT);
        return names.contains(key) || starsEnabled && starred.contains(key);
    }

    public void clearLearned() {
        starred.clear();
    }

    public static boolean hasLeadingStar(String text) {
        if (text == null || text.isEmpty()) return false;

        String clean = AntiVanishText.stripLegacyCodes(text);

        int i = 0;
        while (i < clean.length()) {
            int cp = clean.codePointAt(i);
            i += Character.charCount(cp);

            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp) || cp == 0x200B || cp == 0xFE0F) continue;

            return STARS.contains(cp);
        }

        return false;
    }
}
