package dev.xpple.seedmapper.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;

import java.nio.ByteBuffer;

public class OffsetPosUniforms {
    private static final int SIZE = new Std140SizeCalculator()
        .putVec3()
        .get();

    private static final DynamicGpuDataStorage<Data> STORAGE = new DynamicGpuDataStorageMapped<>("SeedMapper UBO", SIZE, GpuBuffer.USAGE_UNIFORM, 16);

    public static void endFrame() {
        STORAGE.endFrame();
    }

    public static GpuBufferSlice write(float xOffset, float zOffset) {
        return STORAGE.writeData(new Data(xOffset, zOffset));
    }

    private record Data(float xOffset, float zOffset) implements DynamicGpuDataStorage.DynamicGpuData {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                .putVec3(xOffset, 0f, zOffset);
        }
    }
}
