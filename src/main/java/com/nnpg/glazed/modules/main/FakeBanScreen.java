package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FakeBanScreen extends Module {
    public enum Mode {
        TEMP_BAN_ESP("Temp Ban (ESP)"),
        TEMP_BAN_CHEATING("Temp Ban (Cheating)"),
        ACCOUNT_ON_HOLD("Account On Hold");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum TimeUnit {
        DAY_HOURS_MINUTES("Day / Hours / Minutes"),
        SECONDS("Seconds");

        private final String label;

        TimeUnit(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class TimeSettings {
        final Setting<TimeUnit> unit;
        final Setting<Integer> days;
        final Setting<Integer> hours;
        final Setting<Integer> minutes;
        final Setting<Integer> seconds;
        final Setting<String> banId;

        TimeSettings(SettingGroup group, Setting<Mode> mode, Mode own, String suffix, int defaultDays) {
            unit = group.add(new EnumSetting.Builder<TimeUnit>()
                    .name("time-unit" + suffix)
                    .description("Show the time as days, hours and minutes, or as seconds only.")
                    .defaultValue(TimeUnit.DAY_HOURS_MINUTES)
                    .visible(() -> mode.get() == own)
                    .build()
            );

            days = group.add(new IntSetting.Builder()
                    .name("days" + suffix)
                    .description("Days left on the fake ban.")
                    .defaultValue(defaultDays)
                    .min(0)
                    .sliderMax(100)
                    .visible(() -> mode.get() == own && unit.get() == TimeUnit.DAY_HOURS_MINUTES)
                    .build()
            );

            hours = group.add(new IntSetting.Builder()
                    .name("hours" + suffix)
                    .description("Hours left on the fake ban.")
                    .defaultValue(0)
                    .range(0, 23)
                    .sliderRange(0, 23)
                    .visible(() -> mode.get() == own && unit.get() == TimeUnit.DAY_HOURS_MINUTES)
                    .build()
            );

            minutes = group.add(new IntSetting.Builder()
                    .name("minutes" + suffix)
                    .description("Minutes left on the fake ban.")
                    .defaultValue(0)
                    .range(0, 59)
                    .sliderRange(0, 59)
                    .visible(() -> mode.get() == own && unit.get() == TimeUnit.DAY_HOURS_MINUTES)
                    .build()
            );

            seconds = group.add(new IntSetting.Builder()
                    .name("seconds" + suffix)
                    .description("Seconds left on the fake ban.")
                    .defaultValue(30)
                    .range(0, 59)
                    .sliderRange(0, 59)
                    .visible(() -> mode.get() == own && unit.get() == TimeUnit.SECONDS)
                    .build()
            );

            banId = group.add(new StringSetting.Builder()
                    .name("ban-id-(optional)" + suffix)
                    .description("Optional. Leave empty for a random ban ID.")
                    .defaultValue("")
                    .visible(() -> mode.get() == own)
                    .build()
            );
        }

        String text() {
            if (unit.get() == TimeUnit.SECONDS) return seconds.get() + " seconds";

            List<String> parts = new ArrayList<>();
            if (days.get() > 0) parts.add(days.get() + " day");
            if (hours.get() > 0) parts.add(hours.get() + " hours");
            if (minutes.get() > 0) parts.add(minutes.get() + " minutes");
            if (parts.isEmpty()) return "0 minutes";
            return String.join(" ", parts);
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
            .name("screen")
            .description("Which fake screen to show.")
            .defaultValue(Mode.TEMP_BAN_ESP)
            .build()
    );

    private final TimeSettings esp = new TimeSettings(sgGeneral, mode, Mode.TEMP_BAN_ESP, "", 60);
    private final TimeSettings cheating = new TimeSettings(sgGeneral, mode, Mode.TEMP_BAN_CHEATING, "-", 14);

    private final Setting<String> holdDate = sgGeneral.add(new StringSetting.Builder()
            .name("date")
            .description("Optional. Leave empty for today's date.")
            .defaultValue("")
            .visible(() -> mode.get() == Mode.ACCOUNT_ON_HOLD)
            .build()
    );

    private final Setting<String> holdBanId = sgGeneral.add(new StringSetting.Builder()
            .name("ban-id-(optional)--")
            .description("Optional. Leave empty for a random ban ID.")
            .defaultValue("")
            .visible(() -> mode.get() == Mode.ACCOUNT_ON_HOLD)
            .build()
    );

    public FakeBanScreen() {
        super(GlazedAddon.CATEGORY, "fake-ban-screen", "Kicks you from the server with a fake ban screen.");
    }

    @Override
    public void onActivate() {
        toggle();

        if (mc.getConnection() == null) {
            error("You need to be connected to a server.");
            return;
        }

        mc.getConnection().getConnection().disconnect(buildReason());
    }

    private Component buildReason() {
        MutableComponent c = Component.empty();

        switch (mode.get()) {
            case TEMP_BAN_ESP -> {
                add(c, "You are temporarily banned for the use of ESP.", ChatFormatting.RED);
                add(c, "", ChatFormatting.WHITE);
                addLabeled(c, "Time Left: ", esp.text());
                add(c, "", ChatFormatting.WHITE);
                addLabeled(c, "Ban ID: ", normalizeId(esp.banId.get()));
                add(c, "You may be able to appeal this ban on", ChatFormatting.GRAY);
                add(c, "support.donutsmp.net", ChatFormatting.WHITE);
            }
            case TEMP_BAN_CHEATING -> {
                add(c, "You are temporarily banned for cheating.", ChatFormatting.RED);
                add(c, "", ChatFormatting.WHITE);
                addLabeled(c, "Time Left: ", cheating.text());
                add(c, "", ChatFormatting.WHITE);
                addLabeled(c, "Ban ID: ", normalizeId(cheating.banId.get()));
                add(c, "You may be able to appeal this ban on", ChatFormatting.GRAY);
                add(c, "support.donutsmp.net", ChatFormatting.WHITE);
            }
            case ACCOUNT_ON_HOLD -> {
                String shownDate = holdDate.get().trim();
                if (shownDate.isEmpty()) shownDate = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                add(c, "Your account has been placed on hold.", ChatFormatting.WHITE, ChatFormatting.BOLD);
                add(c, "We need to chat with you about something.", ChatFormatting.GRAY);
                add(c, "", ChatFormatting.WHITE);
                add(c, "Please open a ticket in the Donut SMP Discord", ChatFormatting.GRAY);
                add(c, "and we'll get you back as soon as possible!", ChatFormatting.GRAY);
                add(c, "", ChatFormatting.WHITE);
                add(c, "discord.gg/donutsmp", ChatFormatting.YELLOW);
                add(c, "", ChatFormatting.WHITE);
                addLabeled(c, "Date: ", shownDate);
                add(c, "", ChatFormatting.WHITE);
                addLabeled(c, "Ban ID: ", normalizeId(holdBanId.get()));
                add(c, "You may be able to appeal this ban on", ChatFormatting.GRAY);
                add(c, "discord.gg/donutsmp", ChatFormatting.WHITE);
            }
        }

        for (int i = 0; i < 3; i++) add(c, " ", ChatFormatting.WHITE);

        return c;
    }

    private String normalizeId(String raw) {
        String id = raw.trim();
        if (id.isEmpty()) id = randomId();
        if (!id.startsWith("#")) id = "#" + id;
        return id;
    }

    private void add(MutableComponent root, String text, ChatFormatting... styles) {
        if (!root.getSiblings().isEmpty()) root.append(Component.literal("\n"));
        root.append(Component.literal(text).withStyle(styles));
    }

    private void addLabeled(MutableComponent root, String label, String value) {
        if (!root.getSiblings().isEmpty()) root.append(Component.literal("\n"));
        root.append(Component.literal(label).withStyle(ChatFormatting.GRAY));
        root.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    private String randomId() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }
}