package com.nnpg.glazed.slate;

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

public class GlazedSlateTheme extends MeteorGuiTheme {
    public GlazedSlateTheme() {
        super();

        try {
            Field field = GuiTheme.class.getDeclaredField("name");
            field.setAccessible(true);
            field.set(this, "Glazed Slate");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not name the Glazed Slate theme", e);
        }

        applyPalette();
    }

    private void applyPalette() {
        moduleAlignment.set(AlignmentX.Left);

        paint(accentColor, 96, 176, 168, 255);
        paint(checkboxColor, 96, 176, 168, 255);
        paint(plusColor, 96, 200, 140, 255);
        paint(minusColor, 230, 90, 100, 255);
        paint(favoriteColor, 240, 200, 100, 255);

        paint(textColor, 214, 222, 228, 255);
        paint(textSecondaryColor, 130, 142, 150, 255);
        paint(textHighlightColor, 96, 176, 168, 100);
        paint(titleTextColor, 236, 244, 246, 255);
        paint(loggedInColor, 96, 200, 140, 255);
        paint(placeholderColor, 255, 255, 255, 40);

        backgroundColor.get(false, false).set(23, 27, 31, 255);
        backgroundColor.get(false, true, true).set(34, 40, 46, 255);
        backgroundColor.get(true, false).set(62, 128, 122, 255);

        outlineColor.get(false, false).set(44, 53, 60, 255);
        outlineColor.get(false, true, true).set(70, 132, 128, 255);
        outlineColor.get(true, false).set(96, 176, 168, 255);

        paint(moduleBackground, 44, 56, 60, 255);

        paint(separatorText, 200, 208, 214, 255);
        paint(separatorCenter, 70, 84, 92, 255);
        paint(separatorEdges, 70, 84, 92, 110);

        scrollbarColor.get(false, false).set(60, 72, 80, 200);
        scrollbarColor.get(false, true, true).set(80, 100, 108, 230);
        scrollbarColor.get(true, false).set(96, 176, 168, 255);

        sliderHandle.get(false, false).set(190, 214, 212, 255);
        sliderHandle.get(false, true, true).set(236, 248, 246, 255);
        sliderHandle.get(true, false).set(236, 248, 246, 255);

        paint(sliderLeft, 96, 176, 168, 255);
        paint(sliderRight, 36, 42, 50, 255);
    }

    private void paint(Setting<SettingColor> setting, int r, int g, int b, int a) {
        setting.get().set(r, g, b, a);
    }

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new WSlateWindow(icon, title));
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return w(new WSlateButton(text, texture));
    }

    @Override
    public WMinus minus() {
        return w(new WSlateMinus());
    }

    @Override
    public WPlus plus() {
        return w(new WSlatePlus());
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return w(new WSlateCheckbox(checked));
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return w(new WSlateSlider(value, min, max));
    }

    @Override
    public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        return w(new WSlateTextBox(text, placeholder, filter, renderer));
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return w(new WSlateDropdown<>(values, value));
    }

    @Override
    public WTooltip tooltip(String text) {
        return w(new WSlateTooltip(text));
    }

    @Override
    public WTopBar topBar() {
        return w(new WSlateTopBar());
    }

    @Override
    public WWidget module(Module module) {
        return w(new WSlateModule(module, module.title));
    }
}
