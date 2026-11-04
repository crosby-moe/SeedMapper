package dev.xpple.seedmapper.util;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

public record BufferedRegionPos(int x, int z) {
    public static BufferedRegionPos containing(Vec3 vec3) {
        return new BufferedRegionPos(
            Mth.floor(vec3.x) >> 6,
            Mth.floor(vec3.z) >> 6
        );
    }

    public static BufferedRegionPos containing(ChunkPos chunkPos) {
        return new BufferedRegionPos(
            chunkPos.x() >> 2,
            chunkPos.z() >> 2
        );
    }

    public static BufferedRegionPos containing(BlockPos blockPos) {
        return new BufferedRegionPos(
            blockPos.getX() >> 6,
            blockPos.getZ() >> 2
        );
    }

    public int getMinBlockX() {
        return this.x() << 6;
    }

    public int getMinBlockZ() {
        return this.z() << 6;
    }

    public int getMinChunkX() {
        return this.x() << 2;
    }

    public int getMinChunkZ() {
        return this.z() << 2;
    }

    public int getChunkX(int offset) {
        return getMinChunkX() + offset;
    }

    public int getChunkZ(int offset) {
        return getMinChunkZ() + offset;
    }

    public boolean isAnyLoaded(ClientLevel level) {
        return level.getChunk(this.getChunkX(0), this.getChunkZ(0), ChunkStatus.FULL, false) != null
            || level.getChunk(this.getChunkX(0), this.getChunkZ(3), ChunkStatus.FULL, false) != null
            || level.getChunk(this.getChunkX(3), this.getChunkZ(0), ChunkStatus.FULL, false) != null
            || level.getChunk(this.getChunkX(3), this.getChunkZ(3), ChunkStatus.FULL, false) != null;
    }
}
