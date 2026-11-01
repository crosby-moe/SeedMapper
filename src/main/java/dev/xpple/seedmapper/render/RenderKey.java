package dev.xpple.seedmapper.render;

import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;

/// Ensures that every kind of render produced by the mod produces a trivially computable/comparable key
public record RenderKey(ChunkPos chunk, RenderKind renderKind) {
    public sealed interface RenderKind {}
    public record CancellingBoxes(int color) implements RenderKind {}
    public record Rectangles(int color) implements RenderKind {}
    public record CancellingFaces(int color, Direction direction) implements RenderKind {}
}
