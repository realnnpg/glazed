package com.nnpg.glazed.slate;

import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.themes.meteor.widgets.pressable.WMeteorButton;

public class WSlateButton extends WMeteorButton implements GlazedSlateWidget {
    public WSlateButton(String text, GuiTexture texture) {
        super(text, texture);
    }
}
