package com.mozi1924.yefira.encoder;

import com.mozi1924.yefira.compat.VersionCompat;
import com.mozi1924.yefira.compat.visual.VisualEntitySerializer;
import com.mozi1924.yefira.selection.SelectionBox;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Universal scanner for Continuous Visual Entities inside a selection volume.
 * Decoupled from Discrete Voxel Blocks and Block Entities.
 */
public class VisualEntityScanner {

    private VisualEntityScanner() {}

    /**
     * Converts Minecraft Direction to canonical RFC v3.2 facing byte:
     * 0: Down, 1: Up, 2: North, 3: South, 4: West, 5: East, 255: None.
     */
    public static byte toCanonicalFacing(Direction dir) {
        if (dir == null) return (byte) 255;
        return switch (dir) {
            case DOWN -> (byte) 0;
            case UP -> (byte) 1;
            case NORTH -> (byte) 2;
            case SOUTH -> (byte) 3;
            case WEST -> (byte) 4;
            case EAST -> (byte) 5;
        };
    }

    /**
     * Scans for visual entities within the selection bounding box matching the subscribed types.
     */
    public static List<VisualEntityEntry> scanVisualEntities(
            Level level,
            SelectionBox selection,
            Set<String> subscribedEntities
    ) {
        if (level == null || selection == null || subscribedEntities == null || subscribedEntities.isEmpty()) {
            return Collections.emptyList();
        }

        AABB aabb = selection.toAabb();
        List<Entity> candidates = level.getEntitiesOfClass(
                Entity.class,
                aabb,
                e -> e != null && e.isAlive()
        );

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<VisualEntityEntry> result = new ArrayList<>(candidates.size());
        for (Entity e : candidates) {
            VisualEntityEntry entry = toVisualEntityEntry(e, selection, subscribedEntities);
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Converts a live Minecraft Entity to a VisualEntityEntry if it matches subscription and bounds.
     */
    public static VisualEntityEntry toVisualEntityEntry(
            Entity entity,
            SelectionBox selection,
            Set<String> subscribedEntities
    ) {
        if (entity == null || !entity.isAlive()) {
            return null;
        }

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        // Bounding box / continuous intersection check with selection volume
        if (selection != null) {
            AABB selAabb = selection.toAabb();
            AABB entityBb = entity.getBoundingBox();
            boolean inSelection = false;
            if (entityBb != null && entityBb.intersects(selAabb)) {
                inSelection = true;
            } else if (selection.containsContinuous(x, y, z)) {
                inSelection = true;
            } else if (entity instanceof HangingEntity hanging) {
                var pos = hanging.blockPosition();
                if (pos != null && selection.contains(pos)) {
                    inSelection = true;
                }
            }
            if (!inSelection) {
                return null;
            }
        }

        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (subscribedEntities != null && !subscribedEntities.contains(entityId)) {
            return null;
        }

        // Canonical Euler rotation in radians (pitch, yaw, roll)
        float pitch = (float) Math.toRadians(entity.getXRot());
        float yaw = (float) Math.toRadians(entity.getYRot());
        float roll = 0.0f;

        // Scale defaults to (1, 1, 1)
        float sx = 1.0f;
        float sy = 1.0f;
        float sz = 1.0f;

        byte facing = (byte) 255;
        if (entity instanceof HangingEntity hanging) {
            facing = toCanonicalFacing(hanging.getDirection());
        }

        // Custom Payload Extraction
        byte[] payloadBytes = null;
        // 1. Check version-specific compat override first (e.g. Painting)
        String compatPayload = VersionCompat.level().serializeEntityPayloadJson(entity);
        if (compatPayload != null) {
            payloadBytes = compatPayload.getBytes(StandardCharsets.UTF_8);
        } else if (entity instanceof ItemFrame itemFrame) {
            ItemStack stack = itemFrame.getItem();
            String itemId = "";
            int count = 1;
            if (stack != null && !stack.isEmpty()) {
                itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                count = stack.getCount();
            }
            int rot = itemFrame.getRotation();
            boolean isGlowing = (entity instanceof GlowItemFrame);
            boolean isInvisible = entity.isInvisible();
            String json = VisualEntitySerializer.serializeItemFrame(itemId, count, rot, isGlowing, isInvisible, null);
            payloadBytes = json.getBytes(StandardCharsets.UTF_8);
        } else {
            String json = VisualEntitySerializer.serializeGeneric(entityId);
            payloadBytes = json.getBytes(StandardCharsets.UTF_8);
        }

        return new VisualEntityEntry(
                entity.getUUID(),
                entityId,
                x, y, z,
                pitch, yaw, roll,
                sx, sy, sz,
                facing,
                payloadBytes
        );
    }
}
