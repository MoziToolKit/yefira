package com.mozi1924.yefira.encoder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class BlockStateSerializer {

    public static boolean isAirOrIgnored(BlockState state) {
        if (state == null || state.isAir()) {
            return true;
        }
        return state.is(Blocks.STRUCTURE_VOID)
                || state.is(Blocks.CAVE_AIR)
                || state.is(Blocks.VOID_AIR);
    }

    public static String serializeBlockState(BlockState state) {
        if (isAirOrIgnored(state)) {
            return "minecraft:air";
        }
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        Collection<Property<?>> properties = state.getProperties();
        if (properties.isEmpty()) {
            return blockId;
        }

        List<Property<?>> sorted = new ArrayList<>(properties);
        sorted.sort(Comparator.comparing(Property::getName));

        StringBuilder sb = new StringBuilder(blockId).append("[");
        boolean first = true;
        for (Property<?> property : sorted) {
            if (!first) {
                sb.append(",");
            }
            sb.append(property.getName()).append("=").append(getPropertyValueName(state, property));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyValueName(BlockState state, Property<T> property) {
        T val = state.getValue(property);
        return property.getName(val);
    }

    private BlockStateSerializer() {}
}
