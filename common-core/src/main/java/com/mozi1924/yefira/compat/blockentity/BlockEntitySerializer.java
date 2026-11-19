package com.mozi1924.yefira.compat.blockentity;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Canonical JSON encoder for block entity payloads (decorated pot, banner, skull).
 *
 * Pure Java, decoupled from Minecraft and Authlib so every version module shares one
 * implementation. Continuous entities are not serialized here.
 */
public final class BlockEntitySerializer {
    private BlockEntitySerializer() {}

    /**
     * Resolves the canonical skull kind string from a block registry path.
     * E.g. "player_head" -> "player", "skeleton_wall_skull" -> "skeleton".
     */
    public static String resolveSkullKind(String blockPath) {
        if (blockPath == null) return "skeleton";
        if (blockPath.contains("wither_skeleton")) return "wither_skeleton";
        if (blockPath.contains("skeleton")) return "skeleton";
        if (blockPath.contains("zombie")) return "zombie";
        if (blockPath.contains("player")) return "player";
        if (blockPath.contains("creeper")) return "creeper";
        if (blockPath.contains("dragon")) return "dragon";
        if (blockPath.contains("piglin")) return "piglin";
        return "skeleton";
    }

    /**
     * Normalizes a Minecraft Pottery Sherd item ID or pattern name to its canonical pattern stem.
     * E.g. "minecraft:angler_pottery_sherd" -> "angler"
     *      "archer_pottery_pattern" -> "archer"
     *      "brick" / "blank" / "" / null -> null
     */
    public static String normalizeSherd(String item) {
        if (item == null) return null;
        String clean = item.startsWith("minecraft:") ? item.substring(10) : item;
        if (clean.endsWith("_pottery_sherd")) {
            clean = clean.substring(0, clean.length() - 14);
        } else if (clean.endsWith("_pottery_pattern")) {
            clean = clean.substring(0, clean.length() - 16);
        }
        if (clean.isEmpty() || "brick".equals(clean) || "blank".equals(clean)) {
            return null;
        }
        return clean;
    }

    /**
     * Serializes a Decorated Pot into canonical block entity JSON.
     * Sherds are normalized to pattern stems (e.g. "angler") and empty/brick sides serialize to null.
     */
    public static String serializeDecoratedPot(String back, String left, String right, String front) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"id\":\"minecraft:decorated_pot\",\"sherds\":[");
        appendSherd(sb, back);
        sb.append(",");
        appendSherd(sb, left);
        sb.append(",");
        appendSherd(sb, right);
        sb.append(",");
        appendSherd(sb, front);
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Serializes a Banner into canonical block entity JSON.
     *
     * @param baseColor Base dye color name (e.g. "red", "white", "black")
     * @param patterns List of [pattern, color] pairs (e.g. [["stripe_top", "blue"], ["flower", "white"]])
     */
    public static String serializeBanner(String baseColor, java.util.List<String[]> patterns) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"id\":\"minecraft:banner\"");
        if (baseColor != null && !baseColor.isEmpty()) {
            sb.append(",\"base_color\":\"").append(escape(baseColor)).append("\"");
        }
        if (patterns != null && !patterns.isEmpty()) {
            sb.append(",\"patterns\":[");
            boolean first = true;
            for (String[] pat : patterns) {
                if (pat == null || pat.length < 2 || pat[0] == null || pat[1] == null) continue;
                if (!first) sb.append(",");
                sb.append("{\"pattern\":\"").append(escape(pat[0])).append("\",\"color\":\"").append(escape(pat[1])).append("\"}");
                first = false;
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Serializes a Skull/Head into canonical block entity JSON.
     *
     * @param kind "skeleton", "wither_skeleton", "zombie", "player", "creeper", "dragon", "piglin"
     * @param rotation 0..15 for floor skull, or null for wall skull
     * @param name Player profile username (or null)
     * @param id Player profile UUID string (or null)
     * @param skinBase64 Base64 encoded texture payload (or null)
     */
    public static String serializeSkull(
            String kind,
            Integer rotation,
            String name,
            String id,
            String skinBase64
    ) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"id\":\"minecraft:skull\"");
        if (kind != null && !kind.isEmpty()) {
            sb.append(",\"kind\":\"").append(escape(kind)).append("\"");
        }
        if (rotation != null) {
            sb.append(",\"rotation\":").append(rotation);
        }

        String skinUrl = extractSkinUrlFromBase64(skinBase64);
        boolean hasProfile = (name != null && !name.isEmpty())
                || (id != null && !id.isEmpty())
                || (skinUrl != null && !skinUrl.isEmpty())
                || (skinBase64 != null && !skinBase64.isEmpty());

        if (hasProfile) {
            sb.append(",\"profile\":{");
            boolean first = true;
            if (name != null && !name.isEmpty()) {
                sb.append("\"name\":\"").append(escape(name)).append("\"");
                first = false;
            }
            if (id != null && !id.isEmpty()) {
                if (!first) sb.append(",");
                sb.append("\"id\":\"").append(escape(id)).append("\"");
                first = false;
            }
            if (skinUrl != null && !skinUrl.isEmpty()) {
                if (!first) sb.append(",");
                sb.append("\"skin_url\":\"").append(escape(skinUrl)).append("\"");
                first = false;
            }
            if (skinBase64 != null && !skinBase64.isEmpty()) {
                if (!first) sb.append(",");
                sb.append("\"skin_base64\":\"").append(escape(skinBase64)).append("\"");
            }
            sb.append("}");
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Extracts direct texture URL from Minecraft Base64-encoded property JSON.
     * e.g. {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/..."}}}
     */
    public static String extractSkinUrlFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            String json = new String(decoded, StandardCharsets.UTF_8);
            int urlIdx = json.indexOf("\"url\":\"");
            if (urlIdx != -1) {
                int start = urlIdx + 7;
                int end = json.indexOf("\"", start);
                if (end != -1) {
                    return json.substring(start, end);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static void appendSherd(StringBuilder sb, String str) {
        String norm = normalizeSherd(str);
        if (norm != null) {
            sb.append("\"").append(escape(norm)).append("\"");
        } else {
            sb.append("null");
        }
    }

    /**
     * Minimal JSON string escaper: quotes,
     * backslashes and control characters are escaped so a datapack-provided identifier can
     * never produce invalid JSON. Lone surrogates are replaced with U+FFFD.
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
