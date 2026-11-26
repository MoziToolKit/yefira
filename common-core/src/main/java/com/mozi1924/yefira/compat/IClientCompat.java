package com.mozi1924.yefira.compat;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Version-specific abstraction interface for client-only Minecraft APIs
 * (screen handling, input checks, keybindings, checkboxes, permissions, and FOV).
 */
public interface IClientCompat {

    boolean isNoScreenOpen(Minecraft mc);

    boolean isKeyDown(Minecraft mc, int glfwKeyCode);

    void sendClientMessage(Minecraft mc, Component message);

    boolean hasOpPermission(Minecraft mc);

    float getCameraFov(Minecraft mc);

    void openScreen(Minecraft mc, Screen screen);

    Screen createConfigScreen();

    KeyMapping createKeyMapping(String translationKey, int glfwKeyCode);

    Checkbox createCheckbox(
            int x, int y, int width, int height,
            Font font,
            Component message,
            boolean initialValue,
            Consumer<Boolean> onValueChange
    );

    void copyToClipboard(Minecraft mc, String text);

    void attachPasswordMask(EditBox editBox, BooleanSupplier isMasked);
}
