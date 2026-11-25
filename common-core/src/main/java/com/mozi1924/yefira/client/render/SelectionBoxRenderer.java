package com.mozi1924.yefira.client.render;

import com.mozi1924.yefira.client.ghost.GhostModeManager;
import com.mozi1924.yefira.selection.SelectionBox;
import com.mozi1924.yefira.selection.SelectionManager;
import com.mozi1924.yefira.selection.SelectionStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public class SelectionBoxRenderer {

    public static void render(IRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        SelectionManager mgr = SelectionManager.getInstance();
        if (!mgr.hasSelection()) return;

        if (mgr.getDimension() != null && !mc.level.dimension().equals(mgr.getDimension())) {
            return;
        }

        SelectionBox selection = mgr.getCurrentSelection();
        if (selection == null) return;

        BlockPos min = selection.getMin();
        BlockPos max = selection.getMax();
        AABB box = new AABB(
            min.getX(), min.getY(), min.getZ(),
            max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0
        );

        boolean isOversized = selection.isOversized();
        ctx.drawBox(
            box,
            isOversized ? SelectionStyle.COLOR_OVERSIZED_FILL : SelectionStyle.COLOR_NORMAL_FILL,
            isOversized ? SelectionStyle.COLOR_OVERSIZED_STROKE : SelectionStyle.COLOR_NORMAL_STROKE,
            isOversized ? SelectionStyle.WIDTH_OVERSIZED_STROKE : SelectionStyle.WIDTH_NORMAL_STROKE,
            false
        );

        GhostModeManager ghost = GhostModeManager.getInstance();
        if (ghost.isDragging()) {
            SelectionBox previewSel = ghost.getDragPreviewSelection();
            if (previewSel != null) {
                BlockPos pMin = previewSel.getMin();
                BlockPos pMax = previewSel.getMax();
                AABB previewBox = new AABB(
                    pMin.getX(), pMin.getY(), pMin.getZ(),
                    pMax.getX() + 1.0, pMax.getY() + 1.0, pMax.getZ() + 1.0
                );
                boolean previewOversized = previewSel.isOversized();
                ctx.drawBox(
                    previewBox,
                    previewOversized ? SelectionStyle.COLOR_PREVIEW_OVERSIZED_FILL : SelectionStyle.COLOR_PREVIEW_NORMAL_FILL,
                    previewOversized ? SelectionStyle.COLOR_PREVIEW_OVERSIZED_STROKE : SelectionStyle.COLOR_PREVIEW_NORMAL_STROKE,
                    SelectionStyle.WIDTH_PREVIEW_STROKE,
                    false
                );
            }
        }

        ctx.flush();
    }
}
