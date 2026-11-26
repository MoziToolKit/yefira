package com.mozi1924.yefira.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mozi1924.yefira.client.gui.YefiraScreen;
import com.mozi1924.yefira.compat.IClientCompat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ClientCompat implements IClientCompat {

    public static final ClientCompat INSTANCE = new ClientCompat();
    private static final String KEY_CATEGORY = "key.categories.yefira";

    private ClientCompat() {}

    @Override
    public boolean isNoScreenOpen(Minecraft mc) {
        return mc.screen == null;
    }

    @Override
    public boolean isKeyDown(Minecraft mc, int glfwKeyCode) {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), glfwKeyCode);
    }

    @Override
    public void sendClientMessage(Minecraft mc, Component message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(message, false);
        }
    }

    @Override
    public boolean hasOpPermission(Minecraft mc) {
        return mc.player != null && mc.player.hasPermissions(2);
    }

    @Override
    public float getCameraFov(Minecraft mc) {
        return (float) mc.options.fov().get().intValue();
    }

    @Override
    public void openScreen(Minecraft mc, Screen screen) {
        mc.setScreen(screen);
    }

    @Override
    public Screen createConfigScreen() {
        return new YefiraScreen();
    }

    @Override
    public KeyMapping createKeyMapping(String translationKey, int glfwKeyCode) {
        return new KeyMapping(translationKey, InputConstants.Type.KEYSYM, glfwKeyCode, KEY_CATEGORY);
    }

    @Override
    public Checkbox createCheckbox(int x, int y, int width, int height, Font font,
                                   Component message, boolean initialValue,
                                   Consumer<Boolean> onValueChange) {
        return new ConfigCheckbox(x, y, width, height, message, initialValue, onValueChange);
    }

    @Override
    public void copyToClipboard(Minecraft mc, String text) {
        if (mc != null && mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(text != null ? text : "");
        }
    }

    @Override
    public void attachPasswordMask(EditBox editBox, BooleanSupplier isMasked) {
        if (editBox != null) {
            editBox.setFormatter((text, offset) ->
                    isMasked != null && isMasked.getAsBoolean()
                            ? FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY)
                            : FormattedCharSequence.forward(text, Style.EMPTY));
        }
    }

    private static class ConfigCheckbox extends Checkbox {
        private final Consumer<Boolean> onToggle;

        public ConfigCheckbox(int x, int y, int width, int height, Component message,
                              boolean selected, Consumer<Boolean> onToggle) {
            super(x, y, width, height, message, selected);
            this.onToggle = onToggle;
        }

        @Override
        public void onPress() {
            super.onPress();
            if (this.onToggle != null) {
                this.onToggle.accept(this.selected());
            }
        }
    }
}
