package com.mozi1924.yefira.compat.visual;

/**
 * Universal JSON serializer for Continuous Visual Entities (Painting, Item Frame, Armor Stand).
 * Matches Rust EntityKindData schema byte-for-byte.
 */
public final class VisualEntitySerializer {
    private VisualEntitySerializer() {}

    /**
     * Serializes a Painting payload matching Rust PaintingData.
     */
    public static String serializePainting(String variant, int width, int height, int anchorX, int anchorY, int anchorZ) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"type\":\"minecraft:painting\",\"variant\":\"").append(escape(variant != null ? variant : "kebab")).append("\"");
        sb.append(",\"width\":").append(Math.max(1, width));
        sb.append(",\"height\":").append(Math.max(1, height));
        sb.append(",\"anchor_block\":[").append(anchorX).append(",").append(anchorY).append(",").append(anchorZ).append("]}");
        return sb.toString();
    }

    /**
     * Serializes an Item Frame payload matching Rust ItemFrameData.
     */
    public static String serializeItemFrame(String itemId, int count, int rotation, boolean isGlowing, boolean isInvisible, Integer mapId) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"type\":\"minecraft:item_frame\"");
        sb.append(",\"item_id\":\"").append(escape(itemId != null ? itemId : "")).append("\"");
        sb.append(",\"count\":").append(Math.max(1, count));
        sb.append(",\"rotation\":").append(rotation & 7);
        sb.append(",\"is_glowing\":").append(isGlowing);
        sb.append(",\"is_invisible\":").append(isInvisible);
        if (mapId != null) {
            sb.append(",\"map_id\":").append(mapId);
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Serializes an Armor Stand payload matching Rust ArmorStandData.
     */
    public static String serializeArmorStand(boolean showArms, boolean noBasePlate, boolean isSmall) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("{\"type\":\"minecraft:armor_stand\"");
        sb.append(",\"show_arms\":").append(showArms);
        sb.append(",\"no_base_plate\":").append(noBasePlate);
        sb.append(",\"is_small\":").append(isSmall);
        sb.append("}");
        return sb.toString();
    }

    /**
     * Serializes a Generic fallback payload matching Rust EntityKindData::Generic.
     */
    public static String serializeGeneric(String customId) {
        return "{\"type\":\"generic\",\"custom_id\":\"" + escape(customId != null ? customId : "generic") + "\"}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
