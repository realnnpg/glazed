package com.nnpg.glazed.midnight;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.utils.AlignmentX;
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

public class GlazedMidnightTheme extends MeteorGuiTheme {
    public GlazedMidnightTheme() {
        super();

        try {
            Field field = GuiTheme.class.getDeclaredField("name");
            field.setAccessible(true);
            field.set(this, "Glazed Midnight");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not name the Glazed Midnight theme", e);
        }

        applyPalette();
    }

    private void applyPalette() {
        moduleAlignment.set(AlignmentX.Left);

        paint(accentColor, 96, 150, 255, 255);
        paint(checkboxColor, 96, 150, 255, 255);
        paint(plusColor, 70, 255, 200, 255);
        paint(minusColor, 255, 90, 130, 255);
        paint(favoriteColor, 255, 200, 90, 255);

        paint(textColor, 232, 240, 255, 255);
        paint(textSecondaryColor, 140, 155, 200, 255);
        paint(textHighlightColor, 96, 150, 255, 110);
        paint(titleTextColor, 255, 255, 255, 255);
        paint(loggedInColor, 70, 255, 200, 255);
        paint(placeholderColor, 255, 255, 255, 40);

        backgroundColor.get(false, false).set(14, 18, 38, 250);
        backgroundColor.get(false, true, true).set(26, 32, 64, 250);
        backgroundColor.get(true, false).set(40, 52, 100, 250);

        outlineColor.get(false, false).set(50, 62, 128, 255);
        outlineColor.get(false, true, true).set(90, 130, 235, 255);
        outlineColor.get(true, false).set(140, 200, 255, 255);

        paint(moduleBackground, 40, 52, 100, 255);

        paint(separatorText, 214, 226, 255, 255);
        paint(separatorCenter, 100, 150, 255, 255);
        paint(separatorEdges, 100, 150, 255, 60);

        scrollbarColor.get(false, false).set(84, 104, 200, 170);
        scrollbarColor.get(false, true, true).set(110, 140, 235, 210);
        scrollbarColor.get(true, false).set(150, 200, 255, 255);

        sliderHandle.get(false, false).set(226, 236, 255, 255);
        sliderHandle.get(false, true, true).set(245, 250, 255, 255);
        sliderHandle.get(true, false).set(255, 255, 255, 255);

        paint(sliderLeft, 96, 150, 255, 255);
        paint(sliderRight, 24, 30, 60, 255);
    }

    private void paint(Setting<SettingColor> setting, int r, int g, int b, int a) {
        setting.get().set(r, g, b, a);
    }

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WMidnightWindow(icon, title));
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return w(new WMidnightButton(text, texture));
    }

    @Override
    public WMinus minus() {
        return w(new WMidnightMinus());
    }

    @Override
    public WPlus plus() {
        return w(new WMidnightPlus());
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return w(new WMidnightCheckbox(checked));
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return w(new WMidnightSlider(value, min, max));
    }

    @Override
    public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        return w(new WMidnightTextBox(text, placeholder, filter, renderer));
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return w(new WMidnightDropdown<>(values, value));
    }

    @Override
    public WTooltip tooltip(String text) {
        return w(new WMidnightTooltip(text));
    }

    @Override
    public WTopBar topBar() {
        return w(new WMidnightTopBar());
    }

    @Override
    public WWidget module(Module module) {
        return w(new WMidnightModule(module, module.title));
    }
}
