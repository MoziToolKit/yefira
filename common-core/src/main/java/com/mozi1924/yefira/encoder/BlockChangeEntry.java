package com.mozi1924.yefira.encoder;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Immutable record capturing a single block state change event.
 */
public record BlockChangeEntry(BlockPos pos, BlockState state) {}
