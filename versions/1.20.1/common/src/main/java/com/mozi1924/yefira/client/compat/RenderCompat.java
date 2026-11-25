package com.mozi1924.yefira.client.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mozi1924.yefira.client.ghost.GhostModeManager;
import com.mozi1924.yefira.client.gui.IGuiDrawer;
import com.mozi1924.yefira.client.render.IRenderContext;
import com.mozi1924.yefira.client.render.YefiraRenderTypes;
import com.mozi1924.yefira.selection.SelectionStyle;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Compatibility layer for Minecraft 1.20.x rendering pipeline.
 */
public class RenderCompat {

    /**
     * Draws a line segment using Minecraft 1.20.x VertexConsumer API with auto-computed normal.
     */
    public static void drawGizmoLine(PoseStack poseStack, VertexConsumer lines,
                                     Vec3 p1, Vec3 p2,
                                     float r, float g, float b, float a) {
        float dx = (float) (p2.x - p1.x);
        float dy = (float) (p2.y - p1.y);
        float dz = (float) (p2.z - p1.z);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-5f) {
            dx /= len;
            dy /= len;
            dz /= len;
        } else {
            dy = 1.0f;
        }
        drawGizmoLine(poseStack, lines, p1, p2, r, g, b, a, dx, dy, dz);
    }

    /**
     * Draws a line segment using Minecraft 1.20.x VertexConsumer API.
     */
    public static void drawGizmoLine(PoseStack poseStack, VertexConsumer lines,
                                     Vec3 p1, Vec3 p2,
                                     float r, float g, float b, float a,
                                     float dx, float dy, float dz) {
        lines.vertex(poseStack.last().pose(), (float) p1.x, (float) p1.y, (float) p1.z)
             .color(r, g, b, a)
             .normal(poseStack.last().normal(), dx, dy, dz)
             .endVertex();
        lines.vertex(poseStack.last().pose(), (float) p2.x, (float) p2.y, (float) p2.z)
             .color(r, g, b, a)
             .normal(poseStack.last().normal(), dx, dy, dz)
             .endVertex();
    }

    /**
     * Renders a wireframe box.
     */
    public static void renderLineBox(PoseStack poseStack, VertexConsumer lineBuffer, AABB box,
                                     float r, float g, float b, float a) {
        LevelRenderer.renderLineBox(poseStack, lineBuffer, box, r, g, b, a);
    }

    /**
     * Renders a translucent filled box using debugFilledBox.
     */
    public static void renderFilledBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                       float r, float g, float b, float a) {
        DebugRenderer.renderFilledBox(poseStack, bufferSource, box, r, g, b, a);
    }

    /**
     * Renders a translucent selection filled box (depth test on, depth write disabled, no backface culling).
     */
    public static void renderSelectionFilledBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                                float r, float g, float b, float a) {
        VertexConsumer consumer = bufferSource.getBuffer(com.mozi1924.yefira.client.render.YefiraRenderTypes.selectionFilledBox());
        LevelRenderer.addChainedFilledBoxVertices(
            poseStack, consumer,
            box.minX, box.minY, box.minZ,
            box.maxX, box.maxY, box.maxZ,
            r, g, b, a
        );
    }

    /**
     * Renders an Always-On-Top translucent filled box (no depth test).
     */
    public static void renderGizmoFilledBox(PoseStack poseStack, MultiBufferSource bufferSource, AABB box,
                                           float r, float g, float b, float a) {
        VertexConsumer consumer = bufferSource.getBuffer(com.mozi1924.yefira.client.render.YefiraRenderTypes.gizmoFilledBox());
        LevelRenderer.addChainedFilledBoxVertices(
            poseStack, consumer,
            box.minX, box.minY, box.minZ,
            box.maxX, box.maxY, box.maxZ,
            r, g, b, a
        );
    }

    /**
     * Flushes the last batch in bufferSource immediately.
     */
    public static void endLastBatch(MultiBufferSource bufferSource) {
        if (bufferSource instanceof MultiBufferSource.BufferSource bs) {
            bs.endLastBatch();
        }
    }

    public static IGuiDrawer wrapGui(GuiGraphics graphics) {
        return new IGuiDrawer() {
            @Override
            public void fill(int x1, int y1, int x2, int y2, int argbColor) {
                graphics.fill(x1, y1, x2, y2, argbColor);
            }

            @Override
            public void drawText(Font font, Component text, int x, int y, int color, boolean shadow) {
                graphics.drawString(font, text, x, y, color, shadow);
            }

            @Override
            public void drawCenteredText(Font font, Component text, int centerX, int y, int color) {
                graphics.drawCenteredString(font, text, centerX, y, color);
            }
        };
    }

    public static IRenderContext createContext(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera) {
        GhostModeManager ghost = GhostModeManager.getInstance();
        if (ghost.isActive()) {
            ghost.updateRenderedProjection(RenderSystem.getProjectionMatrix());
        }
        Vec3 camPos = camera.getPosition();
        return new IRenderContext() {
            @Override
            public void drawBox(AABB worldBox, int fillColor, int strokeColor, float strokeWidth, boolean alwaysOnTop) {
                AABB rel = worldBox.move(-camPos.x, -camPos.y, -camPos.z);
                float fr = SelectionStyle.r(fillColor);
                float fg = SelectionStyle.g(fillColor);
                float fb = SelectionStyle.b(fillColor);
                float fa = SelectionStyle.a(fillColor);
                if (alwaysOnTop) {
                    renderGizmoFilledBox(poseStack, bufferSource, rel, fr, fg, fb, fa);
                } else {
                    renderSelectionFilledBox(poseStack, bufferSource, rel, fr, fg, fb, fa);
                }

                RenderType lineType;
                if (alwaysOnTop || strokeWidth >= 2.75f) {
                    lineType = YefiraRenderTypes.gizmoLines(strokeWidth);
                } else if (strokeWidth >= 2.25f) {
                    lineType = YefiraRenderTypes.selectionLinesThick();
                } else {
                    lineType = YefiraRenderTypes.selectionLinesNormal();
                }
                VertexConsumer lineBuffer = bufferSource.getBuffer(lineType);
                renderLineBox(
                        poseStack, lineBuffer, rel,
                        SelectionStyle.r(strokeColor),
                        SelectionStyle.g(strokeColor),
                        SelectionStyle.b(strokeColor),
                        SelectionStyle.a(strokeColor)
                );
            }

            @Override
            public void drawPoint(Vec3 worldPos, int argbColor, float pointSize, boolean alwaysOnTop) {
                double half = alwaysOnTop
                        ? Math.max(0.5, worldPos.distanceTo(ghost.getCameraPos())) * 0.008
                        : 0.08;
                Vec3 rel = worldPos.subtract(camPos);
                AABB ptBox = new AABB(rel.x - half, rel.y - half, rel.z - half, rel.x + half, rel.y + half, rel.z + half);
                float r = SelectionStyle.r(argbColor);
                float g = SelectionStyle.g(argbColor);
                float b = SelectionStyle.b(argbColor);
                float a = SelectionStyle.a(argbColor);
                if (alwaysOnTop) {
                    renderGizmoFilledBox(poseStack, bufferSource, ptBox, r, g, b, a);
                } else {
                    renderSelectionFilledBox(poseStack, bufferSource, ptBox, r, g, b, a);
                }
            }

            @Override
            public void drawArrow(Vec3 startWorld, Vec3 endWorld, Vec3 dir, Vec3 u, Vec3 v, int argbColor, float width) {
                Vec3 relStart = startWorld.subtract(camPos);
                Vec3 relTip = endWorld.subtract(camPos);
                float axisLength = (float) endWorld.distanceTo(startWorld);
                float headLen = Math.max(0.1f, Math.min(1.0f, axisLength * 0.1f));
                Vec3 base = relTip.subtract(dir.scale(headLen));

                VertexConsumer lines = bufferSource.getBuffer(YefiraRenderTypes.gizmoLines(width));
                float a = SelectionStyle.a(argbColor);
                float r = SelectionStyle.r(argbColor);
                float g = SelectionStyle.g(argbColor);
                float b = SelectionStyle.b(argbColor);

                drawGizmoLine(poseStack, lines, relStart, relTip, r, g, b, a);
                drawGizmoLine(poseStack, lines, base.add(u.scale(headLen)), relTip, r, g, b, a);
                drawGizmoLine(poseStack, lines, base.add(v.scale(headLen)), relTip, r, g, b, a);
                drawGizmoLine(poseStack, lines, base.subtract(u.scale(headLen)), relTip, r, g, b, a);
                drawGizmoLine(poseStack, lines, base.subtract(v.scale(headLen)), relTip, r, g, b, a);
            }

            @Override
            public void flush() {
                endLastBatch(bufferSource);
            }
        };
    }
}
