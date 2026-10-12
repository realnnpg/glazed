package com.nnpg.glazed.modules.esp;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DroppedItemESP extends Module {
    public enum NotificationMode {
        Chat,
        Toast,
        Both
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgCustom = settings.createGroup("Custom ESP");

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
            .name("range")
            .description("Maximum distance in chunks to show items.")
            .defaultValue(12)
            .min(1)
            .max(32)
            .sliderRange(1, 32)
            .build()
    );

    private final Setting<List<Item>> blacklist = sgGeneral.add(new ItemListSetting.Builder()
            .name("blacklist")
            .description("Items that never get a label.")
            .defaultValue(List.of())
            .build()
    );

    private final Setting<Boolean> showCount = sgGeneral.add(new BoolSetting.Builder()
            .name("show-count")
            .description("Append the stack size to the label.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> heightOffset = sgGeneral.add(new DoubleSetting.Builder()
            .name("height-offset")
            .description("How far above the item the label floats.")
            .defaultValue(0.4)
            .min(0)
            .sliderRange(0, 2)
            .build()
    );

    private final Setting<Double> scale = sgRender.add(new DoubleSetting.Builder()
            .name("scale")
            .description("Label scale.")
            .defaultValue(1.0)
            .min(0.3)
            .sliderRange(0.3, 3)
            .build()
    );

    private final Setting<Boolean> distanceScaling = sgRender.add(new BoolSetting.Builder()
            .name("distance-scaling")
            .description("Shrink labels as items get farther away. Off keeps the text sharp and the same size.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> cornerRadius = sgRender.add(new DoubleSetting.Builder()
            .name("corner-radius")
            .description("Roundness of the label corners. High values make a full pill shape.")
            .defaultValue(8)
            .min(0)
            .sliderRange(0, 12)
            .build()
    );

    private final Setting<SettingColor> textColor = sgRender.add(new ColorSetting.Builder()
            .name("text-color")
            .description("Label text color.")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .build()
    );

    private final Setting<SettingColor> backgroundColor = sgRender.add(new ColorSetting.Builder()
            .name("background-color")
            .description("Label background color.")
            .defaultValue(new SettingColor(48, 24, 72, 190))
            .build()
    );

    private final Setting<Boolean> onlyCustom = sgCustom.add(new BoolSetting.Builder()
            .name("only-esp-custom-list-items")
            .description("Only show labels for items in the custom list.")
            .defaultValue(false)
            .build()
    );

    private final Setting<List<Item>> customItems = sgCustom.add(new ItemListSetting.Builder()
            .name("custom-items")
            .description("Items shown in the custom color.")
            .defaultValue(List.of())
            .build()
    );

    private final Setting<SettingColor> customColor = sgCustom.add(new ColorSetting.Builder()
            .name("custom-color")
            .description("Text color for custom list items.")
            .defaultValue(new SettingColor(255, 200, 0, 255))
            .build()
    );

    private final Setting<Boolean> notifications = sgCustom.add(new BoolSetting.Builder()
            .name("notifications")
            .description("Notify when a custom list item shows up.")
            .defaultValue(true)
            .build()
    );

    private final Setting<NotificationMode> notificationMode = sgCustom.add(new EnumSetting.Builder<NotificationMode>()
            .name("notification-mode")
            .description("How custom list item notifications are shown.")
            .defaultValue(NotificationMode.Both)
            .visible(notifications::get)
            .build()
    );

    private final Set<Integer> notified = new HashSet<>();

    private final Vector3d pos = new Vector3d();

    public DroppedItemESP() {
        super(GlazedAddon.esp, "dropped-item-esp", "Shows the item name above dropped items.");
    }

    @Override
    public void onDeactivate() {
        notified.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null || !notifications.get()) {
            notified.clear();
            return;
        }

        double maxDistance = range.get() * 16.0;
        Set<Integer> seen = new HashSet<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            ItemStack stack = itemEntity.getItem();
            if (!customItems.get().contains(stack.getItem())) continue;
            if (blacklist.get().contains(stack.getItem())) continue;
            if (mc.player.distanceTo(itemEntity) > maxDistance) continue;

            int id = itemEntity.getId();
            seen.add(id);

            if (notified.add(id)) sendNotification(itemEntity);
        }

        notified.retainAll(seen);
    }

    private void sendNotification(ItemEntity itemEntity) {
        ItemStack stack = itemEntity.getItem();
        String name = stack.getHoverName().getString();
        if (stack.getCount() > 1) name += " x" + stack.getCount();

        int x = itemEntity.getBlockX();
        int y = itemEntity.getBlockY();
        int z = itemEntity.getBlockZ();
        String message = name + " detected at " + x + ", " + y + ", " + z;

        NotificationMode mode = notificationMode.get();

        if (mode == NotificationMode.Chat || mode == NotificationMode.Both) {
            info(message);
        }

        if (mode == NotificationMode.Toast || mode == NotificationMode.Both) {
            SystemToast.add(
                    mc.getToastManager(),
                    SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("Dropped Items ESP"),
                    Component.literal(message)
            );
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.level == null || mc.player == null) return;

        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double maxDistance = range.get() * 16.0;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;
            if (mc.player.distanceTo(itemEntity) > maxDistance) continue;

            ItemStack stack = itemEntity.getItem();
            if (blacklist.get().contains(stack.getItem())) continue;

            boolean custom = customItems.get().contains(stack.getItem());
            if (onlyCustom.get() && !custom) continue;

            pos.set(
                    itemEntity.xOld + (itemEntity.getX() - itemEntity.xOld) * tickDelta,
                    itemEntity.yOld + (itemEntity.getY() - itemEntity.yOld) * tickDelta + itemEntity.getBbHeight() + heightOffset.get(),
                    itemEntity.zOld + (itemEntity.getZ() - itemEntity.zOld) * tickDelta
            );

            if (!NametagUtils.to2D(pos, scale.get(), distanceScaling.get())) continue;

            String label = stack.getHoverName().getString();
            if (showCount.get() && stack.getCount() > 1) label += " x" + stack.getCount();

            SettingColor color = custom ? customColor.get() : textColor.get();

            renderLabel(event, label, color);
        }
    }

    private void renderLabel(Render2DEvent event, String label, SettingColor color) {
        TextRenderer text = TextRenderer.get();

        NametagUtils.begin(pos, event.drawContext);

        double width = text.getWidth(label, true);
        double height = text.getHeight(true);
        double x = -width / 2.0;
        double y = -height;

        Renderer2D.COLOR.begin();
        drawRoundedRect(x - 4, y - 2, width + 8, height + 4, cornerRadius.get(), backgroundColor.get());
        Renderer2D.COLOR.render();

        text.beginBig();
        text.render(label, x, y, color, true);
        text.end();

        NametagUtils.end(event.drawContext);
    }

    private void drawRoundedRect(double x, double y, double w, double h, double r, SettingColor color) {
        double radius = Math.min(r, Math.min(w, h) / 2.0);

        if (radius <= 0.5) {
            Renderer2D.COLOR.quad(x, y, w, h, color);
            return;
        }

        int rows = Math.max(1, (int) Math.ceil(radius * 3.0));
        double step = radius / rows;

        for (int i = 0; i < rows; i++) {
            double dy = radius - (i + 0.5) * step;
            double inset = radius - Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
            double rowWidth = w - inset * 2.0;
            Renderer2D.COLOR.quad(x + inset, y + i * step, rowWidth, step, color);
            Renderer2D.COLOR.quad(x + inset, y + h - (i + 1) * step, rowWidth, step, color);
        }

        double middleHeight = h - radius * 2.0;
        if (middleHeight > 0.0) {
            Renderer2D.COLOR.quad(x, y + radius, w, middleHeight, color);
        }
    }
}