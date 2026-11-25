package com.mozi1924.yefira.client.gui;

import com.mozi1924.yefira.client.compat.RenderCompat;
import net.minecraft.client.gui.GuiGraphics;

public class YefiraScreen extends YefiraScreenBase {

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderOverlayText(RenderCompat.wrapGui(graphics));
    }
}
