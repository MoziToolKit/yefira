package com.mozi1924.yefira.client.gui;

import com.mozi1924.yefira.client.compat.ClientCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class YefiraScreen extends YefiraScreenBase {

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderOverlayText(ClientCompat.wrapGui(graphics));
    }
}
