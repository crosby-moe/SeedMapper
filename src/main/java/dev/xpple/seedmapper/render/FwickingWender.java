package dev.xpple.seedmapper.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.xpple.seedmapper.util.BufferedRegionPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FwickingWender {
    public final BufferedRegionPos regionPos;
    public final List<LineBatch> theData = new ArrayList<>();

    // render dater
    private boolean needsUpload = false;

    private @Nullable GpuBuffer theBuffer = null;
    private int indexCount;

    public FwickingWender(BufferedRegionPos regionPos) {
        this.regionPos = regionPos;
    }

    public void append(LineBatch lineBatch) {
        theData.add(lineBatch);
        needsUpload = true;
    }

    public void depend(LineBatch lineBatch) {
        theData.remove(lineBatch);
        needsUpload = true;
    }

    public GpuBufferSlice getVertexBuffer() {
        return theBuffer.slice();
    }

    public boolean isEmpty() {
        return theData.isEmpty();
    }

    public int getIndexCount() {
        return indexCount;
    }

    public void ratioBozo() {
        if (!needsUpload) return;

        float lockedRed = ThreadLocalRandom.current().nextFloat();
        float lockedGreen = ThreadLocalRandom.current().nextFloat();
        float lockedBlue = ThreadLocalRandom.current().nextFloat();

        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(1024)) {
            BufferBuilder buffer = new BufferBuilder(byteBufferBuilder, NoDepthLayer.LINES_NO_DEPTH_PIPELINE.getPrimitiveTopology(), NoDepthLayer.LINES_NO_DEPTH_PIPELINE.getVertexFormatBinding(0));
            for (LineBatch batch : theData) {
                for (Line line : batch.lines()) {
                    Vec3 start = line.start();
                    Vec3 end = line.end();
                    Vec3 normal = end.subtract(start).normalize();
                    int color = line.color();
                    float red = RenderManager.RANDOMIZE_COLORS_PER_BUFFER ? lockedRed : ARGB.redFloat(color);
                    float green = RenderManager.RANDOMIZE_COLORS_PER_BUFFER ? lockedGreen : ARGB.greenFloat(color);
                    float blue = RenderManager.RANDOMIZE_COLORS_PER_BUFFER ? lockedBlue : ARGB.blueFloat(color);

                    buffer
                        .addVertex((float) start.x, (float) start.y, (float) start.z)
                        .setColor(red, green, blue, 1.0F)
                        .setNormal((float) normal.x, (float) normal.y, (float) normal.z)
                        .setLineWidth(2);
                    buffer
                        .addVertex((float) end.x, (float) end.y, (float) end.z)
                        .setColor(red, green, blue, 1.0F)
                        .setNormal((float) normal.x, (float) normal.y, (float) normal.z)
                        .setLineWidth(2);
                }
            }

            try (MeshData meshData = buffer.buildOrThrow()) { // this wont throw though
                if (theBuffer != null) {
                    theBuffer.close();
                }

                theBuffer = RenderSystem.getDevice().createBuffer(() -> "SeedMapper render " + regionPos, GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());
                indexCount = NoDepthLayer.LINES_NO_DEPTH_PIPELINE.getPrimitiveTopology().indexCount((int) (theBuffer.size() / NoDepthLayer.LINES_NO_DEPTH_PIPELINE.getVertexFormatBinding(0).getVertexSize()));
            }
        }
    }

    public void close() {
        this.theBuffer.close();
    }
}
