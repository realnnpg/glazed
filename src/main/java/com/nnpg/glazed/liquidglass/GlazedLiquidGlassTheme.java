package com.nnpg.glazed.liquidglass;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.WTooltip;
import meteordevelopment.meteorclient.gui.widgets.WTopBar;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.lang.reflect.Field;

public class GlazedLiquidGlassTheme extends MeteorGuiTheme {
    public GlazedLiquidGlassTheme() {
        super();

        try {
            Field field = GuiTheme.class.getDeclaredField("name");
            field.setAccessible(true);
            field.set(this, "Glazed Liquid Glass");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not name the Glazed Liquid Glass theme", e);
        }

        applyPalette();
    }

    private void applyPalette() {
        paint(accentColor, 186, 110, 255, 255);
        paint(checkboxColor, 214, 140, 255, 255);
        paint(plusColor, 140, 255, 190, 255);
        paint(minusColor, 255, 120, 160, 255);
        paint(favoriteColor, 255, 200, 120, 255);

        paint(textColor, 250, 244, 255, 255);
        paint(textSecondaryColor, 190, 168, 232, 255);
        paint(textHighlightColor, 186, 110, 255, 110);
        paint(titleTextColor, 255, 255, 255, 255);
        paint(loggedInColor, 150, 255, 200, 255);
        paint(placeholderColor, 255, 255, 255, 45);

        backgroundColor.get(false, false).set(34, 16, 70, 150);
        backgroundColor.get(false, true, true).set(54, 28, 104, 170);
        backgroundColor.get(true, false).set(74, 38, 136, 190);

        outlineColor.get(false, false).set(190, 150, 255, 90);
        outlineColor.get(false, true, true).set(215, 180, 255, 130);
        outlineColor.get(true, false).set(235, 210, 255, 170);

        paint(moduleBackground, 120, 70, 210, 160);

        paint(separatorText, 236, 214, 255, 255);
        paint(separatorCenter, 230, 200, 255, 255);
        paint(separatorEdges, 215, 185, 255, 120);

        scrollbarColor.get(false, false).set(150, 100, 230, 110);
        scrollbarColor.get(false, true, true).set(175, 125, 245, 150);
        scrollbarColor.get(true, false).set(205, 150, 255, 190);

        sliderHandle.get(false, false).set(236, 214, 255, 255);
        sliderHandle.get(false, true, true).set(245, 230, 255, 255);
        sliderHandle.get(true, false).set(255, 244, 255, 255);

        paint(sliderLeft, 160, 96, 255, 255);
        paint(sliderRight, 60, 34, 110, 150);
    }

    private void paint(Setting<SettingColor> setting, int r, int g, int b, int a) {
        setting.get().set(r, g, b, a);
    }

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WLiquidGlassWindow(icon, title));
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return w(new WLiquidGlassButton(text, texture));
    }

    @Override
    public WMinus minus() {
        return w(new WLiquidGlassMinus());
    }

    @Override
    public WPlus plus() {
        return w(new WLiquidGlassPlus());
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return w(new WLiquidGlassCheckbox(checked));
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return w(new WLiquidGlassSlider(value, min, max));
    }

    @Override
    public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        return w(new WLiquidGlassTextBox(text, placeholder, filter, renderer));
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return w(new WLiquidGlassDropdown<>(values, value));
    }

    @Override
    public WTooltip tooltip(String text) {
        return w(new WLiquidGlassTooltip(text));
    }

    @Override
    public WTopBar topBar() {
        return w(new WLiquidGlassTopBar());
    }

    @Override
    public WWidget module(Module module) {
        return w(new WLiquidGlassModule(module, module.title));
    }
}
