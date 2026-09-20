package com.nnpg.glazed.modules.main;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import java.util.Objects;

public class TpaMacro extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> playerName = sgGeneral.add(new StringSetting.Builder()
        .name("target-player")
        .description("Name of the player to send TPA to.")
        .defaultValue("Steve")
        .build()
    );

    private final Setting<TpaType> tpaType = sgGeneral.add(new EnumSetting.Builder<TpaType>()
        .name("tpa-type")
        .description("Command to use.")
        .defaultValue(TpaType.TPA)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between command sends.")
        .defaultValue(40)
        .min(10)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> notifications = sgGeneral.add(new BoolSetting.Builder()
        .name("notifications")
        .description("Show chat feedback.")
        .defaultValue(true)
        .build()
    );

    private int tickCounter = 0;
    private boolean waitingForConfirm = false;
    private long guiWaitStart = 0;
    private static final long GUI_TIMEOUT_MS = 5000;

    public enum TpaType {
        TPA,
        TPAHERE
    }

    public TpaMacro() {
        super(GlazedAddon.CATEGORY, "tpa-macro", "Spams /tpa or /tpahere and clicks the confirmation.");
    }

    @Override
    public void onActivate() {
        tickCounter = 0;
        waitingForConfirm = false;
        guiWaitStart = 0;
        if (notifications.get()) info("Starting TPA to " + playerName.get());
    }

    @Override
    public void onDeactivate() {
        waitingForConfirm = false;
        guiWaitStart = 0;
        if (notifications.get()) info("TPA Macro deactivated.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        Player target = mc.level.players().stream()
            .filter(p -> Objects.equals(p.getGameProfile().name(), playerName.get()))
            .findFirst()
            .orElse(null);

        if (target != null && mc.player.distanceTo(target) < 5) {
            if (notifications.get()) info(" Player " + playerName.get() + " is nearby. Macro complete.");
            toggle();
            return;
        }

        if (waitingForConfirm && mc.gui.screen() instanceof AbstractContainerScreen<?>) {
            clickConfirmButtonIfPresent();
            return;
        }

        if (waitingForConfirm && guiWaitStart > 0 && System.currentTimeMillis() - guiWaitStart > GUI_TIMEOUT_MS) {
            if (notifications.get()) ChatUtils.warning("GUI timeout. Retrying...");
            waitingForConfirm = false;
            guiWaitStart = 0;
        }

        if (!waitingForConfirm) {
            tickCounter++;
            if (tickCounter >= delay.get()) {
                String command = (tpaType.get() == TpaType.TPA ? "/tpa " : "/tpahere ") + playerName.get();
                ChatUtils.sendPlayerMsg(command);
                if (notifications.get()) ChatUtils.info("📨 Sent: " + command);
                waitingForConfirm = true;
                guiWaitStart = System.currentTimeMillis();
                tickCounter = 0;
            }
        }
    }

    private void clickConfirmButtonIfPresent() {
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;

        var handler = screen.getMenu();

        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getItem();
            String key = stack.getItem().getDescriptionId().toLowerCase();

            if (key.contains("stained_glass_pane") && key.contains("lime")) {
                mc.gameMode.handleContainerInput(handler.containerId, i, 0, ContainerInput.PICKUP, mc.player);
                if (notifications.get()) ChatUtils.info("🟢 Confirm button clicked (slot " + i + ").");
                mc.player.closeContainer();
                waitingForConfirm = false;
                guiWaitStart = 0;
                return;
            }
        }
        if (notifications.get()) ChatUtils.warning("No button's found in GUI.");
    }
}
