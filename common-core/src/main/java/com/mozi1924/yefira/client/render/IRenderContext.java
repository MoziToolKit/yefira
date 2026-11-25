package com.mozi1924.yefira.client.render;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Minimal 3D world overlay rendering abstraction bridging custom VertexConsumer
 * pipelines (1.20.1-1.21.4) and native Gizmos (26.2).
 */
public interface IRenderContext {

    void drawBox(AABB worldBox, int fillColor, int strokeColor, float strokeWidth, boolean alwaysOnTop);

    void drawPoint(Vec3 worldPos, int argbColor, float pointSize, boolean alwaysOnTop);

    void drawArrow(Vec3 startWorld, Vec3 endWorld, Vec3 dir, Vec3 u, Vec3 v, int argbColor, float width);

    default void flush() {}
}
