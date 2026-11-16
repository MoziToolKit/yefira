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
     *
     * @param mapId            Filled-map id, or null when the held item is not a map.
     * @param blockEntityJson  Canonical `block_entity` JSON value (decorated pot / banner /
     *                         skull) produced by {@link VisualBlockEntity}, or null.
     */
    public static String serializeItemFrame(
            String itemId,
            int count,
            int rotation,
            boolean isGlowing,
            boolean isInvisible,
            Integer mapId,
            String blockEntityJson
    ) {
        StringBuilder sb = new StringBuilder(192);
        sb.append("{\"type\":\"minecraft:item_frame\"");
        sb.append(",\"item_id\":\"").append(escape(itemId != null ? itemId : "")).append("\"");
        sb.append(",\"count\":").append(Math.max(1, count));
        sb.append(",\"rotation\":").append(rotation & 7);
        sb.append(",\"is_glowing\":").append(isGlowing);
        sb.append(",\"is_invisible\":").append(isInvisible);
        if (mapId != null) {
            sb.append(",\"map_id\":").append(mapId);
        }
        if (blockEntityJson != null && !blockEntityJson.isEmpty()) {
            sb.append(",\"components\":{\"block_entity\":").append(blockEntityJson).append("}");
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

    /**
     * Minimal JSON string escaper. Escapes quotes, backslashes and every control
     * character (including newlines and tabs) so that arbitrary datapack/mod identifiers
     * can never produce invalid JSON. Lone surrogates are replaced with U+FFFD because
     * they are not representable in UTF-8 and would make the payload undecodable.
     */
    static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else if (Character.isHighSurrogate(c)
                            && i + 1 < s.length()
                            && Character.isLowSurrogate(s.charAt(i + 1))) {
                        out.append(c).append(s.charAt(++i));
                    } else if (Character.isSurrogate(c)) {
                        out.append('\ufffd');
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
