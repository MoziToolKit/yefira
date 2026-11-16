package com.mozi1924.yefira.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Version-specific abstraction interface for Level and Registry operations.
 * Handles discrepancies such as ResourceLocation vs Identifier,
 * getMinSection() vs getMinSectionY(), and Biome registry extraction.
 */
public interface ILevelCompat {

    /**
     * Extracts the namespaced ID (e.g. "minecraft:overworld") from an active Level.
     */
    String getDimensionId(Level level);

    /**
     * Extracts the namespaced ID from a Level ResourceKey.
     */
    String getDimensionId(ResourceKey<Level> dimension);

    /**
     * Parses a dimension string into a Level ResourceKey.
     */
    ResourceKey<Level> parseDimensionKey(String dimStr);

    /**
     * Retrieves the namespaced ID of the biome at the given BlockPos.
     */
    String getBiomeId(Level level, BlockPos pos);

    /**
     * Returns the minimum chunk section coordinate on the Y axis.
     */
    int getMinSectionY(Level level);

    /**
     * Returns the maximum chunk section coordinate on the Y axis.
     */
    int getMaxSectionY(Level level);

    /**
     * Checks if the command source has administrative/operator permissions.
     */
    boolean hasAdminPermission(net.minecraft.commands.CommandSourceStack source);

    /**
     * Retrieves the 64 quart biomes (4x4x4) for the given section (secX, secY, secZ).
     * Returns an array of 64 namespaced biome IDs indexed as (qy &lt;&lt; 4) | (qz &lt;&lt; 2) | qx.
     */
    String[] getSectionQuartBiomes(Level level, int secX, int secY, int secZ);

    /**
     * Serializes a BlockEntity to JSON if supported (e.g. decorated pot), or returns null.
     */
    default String serializeBlockEntityJson(net.minecraft.world.level.block.entity.BlockEntity be) {
        return null;
    }

    /**
     * Serializes a visual entity's custom payload to JSON string if supported, or returns null.
     */
    default String serializeEntityPayloadJson(net.minecraft.world.entity.Entity entity) {
        return null;
    }

    /**
     * Serializes the held ItemStack's BlockEntity-backed visual components (decorated pot
     * sherds, banner patterns, skull profile) into the canonical `block_entity` JSON value
     * expected by `mtk_model::BlockEntityData`. Returns null when the item carries none.
     *
     * <p>Versions without a Data Components API return null and simply omit the payload.
     */
    default String serializeItemBlockEntityJson(net.minecraft.world.item.ItemStack stack) {
        return null;
    }

    /**
     * Returns the map id carried by a filled-map ItemStack, or null when absent.
     *
     * <p>Versions without a Data Components API return null and simply omit the field.
     */
    default Integer getItemMapId(net.minecraft.world.item.ItemStack stack) {
        return null;
    }
}
