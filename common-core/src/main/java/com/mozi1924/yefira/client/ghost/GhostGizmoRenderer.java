package com.mozi1924.yefira.client.ghost;

import com.mozi1924.yefira.client.render.IRenderContext;
import com.mozi1924.yefira.selection.SelectionBox;
import com.mozi1924.yefira.selection.SelectionManager;
import com.mozi1924.yefira.selection.SelectionStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class GhostGizmoRenderer {

    public static final int COLOR_X = SelectionStyle.COLOR_HANDLE_X;
    public static final int COLOR_Y = SelectionStyle.COLOR_HANDLE_Y;
    public static final int COLOR_Z = SelectionStyle.COLOR_HANDLE_Z;
    public static final int COLOR_CENTER = SelectionStyle.COLOR_HANDLE_CENTER;
    public static final int COLOR_HIGHLIGHT = SelectionStyle.COLOR_HANDLE_HIGHLIGHT;

    public static void render(IRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        GhostModeManager ghost = GhostModeManager.getInstance();
        if (!ghost.isActive()) return;

        SelectionManager mgr = SelectionManager.getInstance();

        if (!ghost.isDragging() && !ghost.isBoxCreating() && !mgr.hasSelection() && ghost.getHoveredBlockPos() != null) {
            BlockPos hPos = ghost.getHoveredBlockPos();
            AABB blockBox = new AABB(
                hPos.getX(), hPos.getY(), hPos.getZ(),
                hPos.getX() + 1.0, hPos.getY() + 1.0, hPos.getZ() + 1.0
            );
            ctx.drawBox(
                blockBox,
                SelectionStyle.COLOR_HOVER_BLOCK_FILL,
                SelectionStyle.COLOR_HOVER_BLOCK_STROKE,
                SelectionStyle.WIDTH_HOVER_BLOCK_STROKE,
                false
            );
        }

        if (ghost.isBoxCreating()) {
            SelectionBox boxCreate = ghost.getBoxCreateSelection();
            if (boxCreate != null) {
                BlockPos bMin = boxCreate.getMin();
                BlockPos bMax = boxCreate.getMax();
                AABB createBox = new AABB(
                    bMin.getX(), bMin.getY(), bMin.getZ(),
                    bMax.getX() + 1.0, bMax.getY() + 1.0, bMax.getZ() + 1.0
                );
                boolean oversized = boxCreate.isOversized();
                int strokeColor = oversized ? SelectionStyle.COLOR_BOX_CREATE_OVERSIZED_STROKE : SelectionStyle.COLOR_BOX_CREATE_NORMAL_STROKE;
                int fillColor = oversized ? SelectionStyle.COLOR_BOX_CREATE_OVERSIZED_FILL : SelectionStyle.COLOR_BOX_CREATE_NORMAL_FILL;
                ctx.drawBox(createBox, fillColor, strokeColor, SelectionStyle.WIDTH_BOX_CREATE_STROKE, false);

                Vec3 startCorner = new Vec3(bMin.getX() + 0.5, bMin.getY() + 0.5, bMin.getZ() + 0.5);
                Vec3 endCorner = new Vec3(bMax.getX() + 0.5, bMax.getY() + 0.5, bMax.getZ() + 0.5);
                ctx.drawPoint(startCorner, strokeColor, SelectionStyle.CORNER_POINT_SIZE, false);
                ctx.drawPoint(
                    endCorner,
                    oversized ? SelectionStyle.COLOR_BOX_CREATE_CORNER_END_OVERSIZED : SelectionStyle.COLOR_BOX_CREATE_CORNER_END,
                    SelectionStyle.CORNER_POINT_SIZE,
                    false
                );
                ctx.flush();
            }
            return;
        }

        if (mgr.hasSelection() && mgr.getDimension() != null && !mc.level.dimension().equals(mgr.getDimension())) {
            ctx.flush();
            return;
        }

        BlockPos pos1 = ghost.getEffectivePos1();
        BlockPos pos2 = ghost.getEffectivePos2();

        if (pos1 != null) {
            Vec3 origin1 = new Vec3(pos1.getX() + 0.5, pos1.getY() + 0.5, pos1.getZ() + 0.5);
            renderCornerGizmo(ctx, origin1, GhostModeManager.CORNER_POS1);
        }

        if (pos2 != null) {
            Vec3 origin2 = new Vec3(pos2.getX() + 0.5, pos2.getY() + 0.5, pos2.getZ() + 0.5);
            renderCornerGizmo(ctx, origin2, GhostModeManager.CORNER_POS2);
        }

        if (pos1 != null && pos2 != null) {
            BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ())
            );
            BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ())
            );
            Vec3 center = new Vec3(
                (min.getX() + max.getX() + 1) / 2.0,
                (min.getY() + max.getY() + 1) / 2.0,
                (min.getZ() + max.getZ() + 1) / 2.0
            );
            renderCenterGizmo(ctx, center);
        }

        ctx.flush();
    }

    private static void renderCornerGizmo(IRenderContext ctx, Vec3 origin, int cornerId) {
        GhostModeManager ghost = GhostModeManager.getInstance();
        Vec3 cameraPos = ghost.getCameraPos();
        double dist = Math.max(0.5, origin.distanceTo(cameraPos));
        float axisLength = (float) (dist * SelectionStyle.HANDLE_AXIS_RATIO);
        float centerHalf = (float) (dist * SelectionStyle.HANDLE_CENTER_RATIO);

        int hoveredCorner = ghost.getHoveredCorner();
        int hoveredAxis = ghost.getHoveredAxis();
        int draggingCorner = ghost.getDraggingCorner();
        int draggingAxis = ghost.getDraggingAxis();

        boolean isCurrentCorner = (draggingCorner == cornerId) || (draggingCorner == GhostModeManager.CORNER_NONE && hoveredCorner == cornerId);
        int activeAxis = draggingCorner != GhostModeManager.CORNER_NONE ? draggingAxis : hoveredAxis;

        boolean centerHighlighted = isCurrentCorner && (activeAxis == GhostModeManager.AXIS_CENTER);
        int centerColor = centerHighlighted ? COLOR_HIGHLIGHT : COLOR_CENTER;
        AABB centerBox = new AABB(
            origin.x - centerHalf, origin.y - centerHalf, origin.z - centerHalf,
            origin.x + centerHalf, origin.y + centerHalf, origin.z + centerHalf
        );
        ctx.drawBox(centerBox, centerColor & 0x88FFFFFF, centerColor, 3.0f, true);

        boolean xHighlighted = isCurrentCorner && (activeAxis == GhostModeManager.AXIS_X);
        ctx.drawArrow(origin, origin.add(axisLength, 0, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                xHighlighted ? COLOR_HIGHLIGHT : COLOR_X, xHighlighted ? 5.0f : 3.0f);

        boolean yHighlighted = isCurrentCorner && (activeAxis == GhostModeManager.AXIS_Y);
        ctx.drawArrow(origin, origin.add(0, axisLength, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                yHighlighted ? COLOR_HIGHLIGHT : COLOR_Y, yHighlighted ? 5.0f : 3.0f);

        boolean zHighlighted = isCurrentCorner && (activeAxis == GhostModeManager.AXIS_Z);
        ctx.drawArrow(origin, origin.add(0, 0, axisLength), new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 1, 0),
                zHighlighted ? COLOR_HIGHLIGHT : COLOR_Z, zHighlighted ? 5.0f : 3.0f);
    }

    private static void renderCenterGizmo(IRenderContext ctx, Vec3 center) {
        GhostModeManager ghost = GhostModeManager.getInstance();
        Vec3 cameraPos = ghost.getCameraPos();
        double dist = Math.max(0.5, center.distanceTo(cameraPos));
        float axisLength = (float) (dist * SelectionStyle.HANDLE_AXIS_RATIO);

        int hoveredCorner = ghost.getHoveredCorner();
        int hoveredAxis = ghost.getHoveredAxis();
        int draggingCorner = ghost.getDraggingCorner();
        int draggingAxis = ghost.getDraggingAxis();

        boolean isCenterGizmo = (draggingCorner == GhostModeManager.CORNER_CENTER) ||
                                (draggingCorner == GhostModeManager.CORNER_NONE && hoveredCorner == GhostModeManager.CORNER_CENTER);
        int activeAxis = draggingCorner != GhostModeManager.CORNER_NONE ? draggingAxis : hoveredAxis;

        boolean centerHighlighted = isCenterGizmo && (activeAxis == GhostModeManager.AXIS_CENTER);
        ctx.drawPoint(center, centerHighlighted ? COLOR_HIGHLIGHT : COLOR_CENTER, 7.0f, true);

        boolean xHighlighted = isCenterGizmo && (activeAxis == GhostModeManager.AXIS_X);
        ctx.drawArrow(center, center.add(axisLength, 0, 0), new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                xHighlighted ? COLOR_HIGHLIGHT : (COLOR_X & 0xAAFFFFFF), xHighlighted ? 4.0f : 2.0f);

        boolean yHighlighted = isCenterGizmo && (activeAxis == GhostModeManager.AXIS_Y);
        ctx.drawArrow(center, center.add(0, axisLength, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                yHighlighted ? COLOR_HIGHLIGHT : (COLOR_Y & 0xAAFFFFFF), yHighlighted ? 4.0f : 2.0f);

        boolean zHighlighted = isCenterGizmo && (activeAxis == GhostModeManager.AXIS_Z);
        ctx.drawArrow(center, center.add(0, 0, axisLength), new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 1, 0),
                zHighlighted ? COLOR_HIGHLIGHT : (COLOR_Z & 0xAAFFFFFF), zHighlighted ? 4.0f : 2.0f);
    }
}
