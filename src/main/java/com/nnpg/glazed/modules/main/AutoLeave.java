package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.render.MeteorToast;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class AutoLeave extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgArming = settings.createGroup("Arming");

    private static final Set<String> PERMANENT_WHITELIST = new HashSet<>(Arrays.asList(
        "FreeCamera"
    ));

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Only react to players within this many blocks. 0 means anywhere you can see them, which is your whole render distance.")
        .defaultValue(0)
        .min(0)
        .sliderMax(256)
        .build()
    );

    private final Setting<Integer> reactionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("reaction-delay")
        .description("Ticks to wait between spotting someone and pulling the plug. 0 disconnects on the same tick.")
        .defaultValue(0)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> reactionJitter = sgGeneral.add(new IntSetting.Builder()
        .name("reaction-jitter")
        .description("Randomly adds up to this many ticks to the reaction delay so your logouts are not all the same length.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .visible(() -> reactionDelay.get() > 0)
        .build()
    );

    private final Setting<Integer> joinGrace = sgGeneral.add(new IntSetting.Builder()
        .name("join-grace")
        .description("Ticks to ignore after joining a world, so the module does not fire while chunks and entities are still streaming in.")
        .defaultValue(40)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<String> reason = sgGeneral.add(new StringSetting.Builder()
        .name("reason")
        .description("Text shown on your own disconnect screen. The server never sees it.")
        .defaultValue("Auto Leave: player detected")
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Say in chat what it is watching and who set it off.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> toast = sgGeneral.add(new BoolSetting.Builder()
        .name("toast")
        .description("Also pop a toast when it triggers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> whitelist = sgFilter.add(new StringListSetting.Builder()
        .name("whitelist")
        .description("Player names that never trigger a leave.")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Treat everyone on your Meteor friends list as safe.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreAdmins = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-admins")
        .description("Treat everyone in the Admin List module as safe. Turn this off if staff are exactly who you are hiding from.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreNpcs = sgFilter.add(new BoolSetting.Builder()
        .name("ignore-npcs")
        .description("Skip player entities that have no entry in the tab list. Server shop NPCs are built this way, real players are not.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> armOnStartup = sgArming.add(new BoolSetting.Builder()
        .name("arm-on-startup")
        .description("Always save this module as enabled, so it is live again the next time you launch the game even though leaving turned it off. Turn this off if you want it to stay off.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> disableOnLeave = sgArming.add(new BoolSetting.Builder()
        .name("disable-on-leave")
        .description("Turn the module off whenever you leave a server, however you left it.")
        .defaultValue(true)
        .build()
    );

    private final Random random = new Random();

    private int graceCounter;
    private int fuse = -1;
    private String trigger;
    private boolean leaving;

    public AutoLeave() {
        super(GlazedAddon.CATEGORY, "auto-leave", "Disconnects as soon as another player shows up in your render distance.");
    }

    @Override
    public void onActivate() {
        graceCounter = joinGrace.get();
        fuse = -1;
        trigger = null;
        leaving = false;

        if (notifications.get()) {
            if (range.get() <= 0) info("Armed. Leaving as soon as any player loads in.");
            else info("Armed. Leaving as soon as a player comes within (highlight)%.0f(default) blocks.", range.get());
        }
    }

    @Override
    public void onDeactivate() {
        fuse = -1;
        trigger = null;
        leaving = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (leaving) return;
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        if (graceCounter > 0) {
            graceCounter--;
            return;
        }

        if (fuse > 0) {
            fuse--;
            return;
        }

        if (fuse == 0) {
            leave();
            return;
        }

        String found = findPlayer();
        if (found == null) return;

        trigger = found;

        if (notifications.get()) info("(highlight)%s(default) is here. Leaving.", trigger);
        if (toast.get()) mc.getToastManager().addToast(new MeteorToast.Builder(title).text("Player detected - leaving").icon(Items.PLAYER_HEAD).build());

        fuse = reactionDelay.get();
        if (fuse > 0 && reactionJitter.get() > 0) fuse += random.nextInt(reactionJitter.get() + 1);
        if (fuse == 0) leave();
    }

    private String findPlayer() {
        String self = mc.player.getGameProfile().name();
        double limit = range.get();
        AdminList adminList = ignoreAdmins.get() ? Modules.get().get(AdminList.class) : null;

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (player instanceof FakePlayerEntity) continue;
            if (!player.isAlive()) continue;

            String name = player.getGameProfile().name();
            if (name == null || name.equals(self)) continue;

            if (PERMANENT_WHITELIST.contains(name)) continue;
            if (whitelist.get().contains(name)) continue;
            if (ignoreFriends.get() && Friends.get().isFriend(player)) continue;
            if (adminList != null && adminList.isAdmin(name)) continue;
            if (ignoreNpcs.get() && mc.getConnection().getPlayerInfo(player.getUUID()) == null) continue;

            if (limit > 0 && mc.player.distanceTo(player) > limit) continue;

            return name;
        }

        return null;
    }

    private void leave() {
        fuse = -1;

        if (mc.getConnection() == null) {
            warning("No connection to drop, turning off.");
            toggle();
            return;
        }

        leaving = true;
        String text = reason.get().isBlank() ? "Auto Leave" : reason.get();
        if (trigger != null) text = text + " (" + trigger + ")";
        mc.getConnection().getConnection().disconnect(Component.literal(text));
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (!disableOnLeave.get()) {
            graceCounter = joinGrace.get();
            fuse = -1;
            leaving = false;
            return;
        }

        if (notifications.get() && trigger != null) info("Left because of (highlight)%s(default). Turning off.", trigger);
        else if (notifications.get()) info("Left the server. Turning off.");

        toggle();
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = super.toTag();
        if (tag != null && armOnStartup.get()) tag.putBoolean("active", true);
        return tag;
    }

    @Override
    public String getInfoString() {
        if (trigger != null) return trigger;
        if (graceCounter > 0) return "grace";
        return null;
    }
}
