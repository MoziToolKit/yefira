package com.mozi1924.yefira.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Minimal 2D GUI drawing abstraction bridging GuiGraphics (1.20.1-1.21.4)
 * and GuiGraphicsExtractor (26.2).
 */
public interface IGuiDrawer {

    void fill(int x1, int y1, int x2, int y2, int argbColor);

    void drawText(Font font, Component text, int x, int y, int color, boolean shadow);

    void drawCenteredText(Font font, Component text, int centerX, int y, int color);
}
