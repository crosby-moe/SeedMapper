package dev.xpple.seedmapper.seedmap;

import net.minecraft.core.BlockPos;

public record FeatureData(MapFeature feature, MapFeature.Texture texture, BlockPos pos) {}
