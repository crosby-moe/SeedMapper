package dev.xpple.seedmapper.render;

import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import dev.xpple.seedmapper.config.Configs;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Collectors;

public final class RenderManager {

    private RenderManager() {
    }

    private static final RenderStateDataKey<List<Map.Entry<RenderKey, FwickingWender>>> LINES_SET_KEY = RenderStateDataKey.create(() -> "SeedMapper lines set");

    private static Map<RenderKey, FwickingWender> renders = Collections.emptyMap();
    static {
        rebuildLineSet();
    }

    public static void rebuildLineSet() {
        Map<RenderKey, FwickingWender> temp = renders;
        renders = CacheBuilder.newBuilder().expireAfterWrite(Configs.HighlightDuration).<RenderKey, FwickingWender>build().asMap();
        renders.putAll(temp);
        temp.clear(); // todo this might cause memory issues
    }

    private static void drawLines(Collection<Line> lines, RenderKey.RenderKind renderKind) {
        Map<ChunkPos, List<Line>> separated = lines.stream().collect(Collectors.groupingBy(line -> ChunkPos.containing(BlockPos.containing(line.start()))));
        separated.entrySet().forEach(entry -> entry.setValue(entry.getValue().stream().map(line -> line.offset(Vec3.atLowerCornerOf(entry.getKey().getWorldPosition()))).toList()));

        try (ByteBufferBuilder byteBufferBuilder = new ByteBufferBuilder(1024)) {
            separated.forEach((chunkPos, groupLines) -> {
                BufferBuilder buffer = new BufferBuilder(byteBufferBuilder, NoDepthLayer.LINES_NO_DEPTH_PIPELINE.getPrimitiveTopology(), NoDepthLayer.LINES_NO_DEPTH_PIPELINE.getVertexFormatBinding(0));

                for (Line line : groupLines) {
                    Vec3 start = line.start();
                    Vec3 end = line.end();
                    Vec3 normal = end.subtract(start).normalize();
                    int color = line.color();
                    float red = ARGB.redFloat(color);
                    float green = ARGB.greenFloat(color);
                    float blue = ARGB.blueFloat(color);

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

                try (MeshData meshData = buffer.buildOrThrow()) { // this wont throw though
                    RenderKey renderKey = new RenderKey(chunkPos, renderKind);
                    GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> "SeedMapper render " + renderKey, GpuBuffer.USAGE_VERTEX, meshData.vertexBuffer());

                    renders.put(renderKey, new FwickingWender(vertices));
                }
            });
        }
    }

    public static void drawCancellingBoxes(Collection<BlockPos> posBatch, int color) {
        Collection<Line> batchLines = CancellingEdges.create(posBatch, color, null);
        if (batchLines != null) {
            drawLines(batchLines, new RenderKey.CancellingBoxes(color));
            return;
        }

        Set<Line> lines = new ObjectOpenHashSet<>();

        posBatch.forEach(pos -> {
            Vec3 minPosition = new Vec3(pos);
            Vec3 size = new Vec3(1, 1, 1);
            addCancellingLine(new Line(minPosition, minPosition.add(size.x(), 0, 0), color), lines);
            addCancellingLine(new Line(minPosition, minPosition.add(0, size.y(), 0), color), lines);
            addCancellingLine(new Line(minPosition, minPosition.add(0, 0, size.z()), color), lines);
            addCancellingLine(new Line(minPosition.add(size.x(), 0, size.z()), minPosition.add(size.x(), 0, 0), color), lines);
            addCancellingLine(new Line(minPosition.add(size.x(), 0, size.z()), minPosition.add(size.x(), size.y(), size.z()), color), lines);
            addCancellingLine(new Line(minPosition.add(size.x(), 0, size.z()), minPosition.add(0, 0, size.z()), color), lines);
            addCancellingLine(new Line(minPosition.add(size.x(), size.y(), 0), minPosition.add(size.x(), 0, 0), color), lines);
            addCancellingLine(new Line(minPosition.add(size.x(), size.y(), 0), minPosition.add(0, size.y(), 0), color), lines);
            addCancellingLine(new Line(minPosition.add(size.x(), size.y(), 0), minPosition.add(size.x(), size.y(), size.z()), color), lines);
            addCancellingLine(new Line(minPosition.add(0, size.y(), size.z()), minPosition.add(0, 0, size.z()), color), lines);
            addCancellingLine(new Line(minPosition.add(0, size.y(), size.z()), minPosition.add(0, size.y(), 0), color), lines);
            addCancellingLine(new Line(minPosition.add(0, size.y(), size.z()), minPosition.add(size.x(), size.y(), size.z()), color), lines);
        });
        drawLines(lines, new RenderKey.CancellingBoxes(color));
    }

    public static void drawRectangles(Collection<AABB> rectangles, int color) {
        Set<Line> lines = new ObjectOpenHashSet<>();
        for (AABB rectangle : rectangles) {
            double y = (rectangle.minY + rectangle.maxY) / 2;
            Vec3 minPosition = new Vec3(rectangle.minX, y, rectangle.minZ);
            Vec3 maxPosition = new Vec3(rectangle.maxX, y, rectangle.maxZ);
            lines.add(new Line(minPosition, new Vec3(maxPosition.x, y, minPosition.z), color));
            lines.add(new Line(minPosition, new Vec3(minPosition.x, y, maxPosition.z), color));
            lines.add(new Line(maxPosition, new Vec3(maxPosition.x, y, minPosition.z), color));
            lines.add(new Line(maxPosition, new Vec3(minPosition.x, y, maxPosition.z), color));
        }
        drawLines(lines, new RenderKey.Rectangles(color));
    }

    public static void drawCancellingFaces(Collection<BlockPos> posBatch, int color, Direction direction) {
        Collection<Line> batchLines = CancellingEdges.create(posBatch, color, direction);
        if (batchLines != null) {
            drawLines(batchLines, new RenderKey.CancellingFaces(color, direction));
            return;
        }

        Set<Line> lines = new HashSet<>();

        posBatch.forEach(pos -> {
            Vec3 minPosition = new Vec3(pos);
            Vec3 maxPosition = minPosition.add(1, 1, 1);

            switch (direction) {
                case DOWN -> {
                    addCancellingLine(new Line(minPosition, minPosition.add(1, 0, 0), color), lines);
                    addCancellingLine(new Line(minPosition, minPosition.add(0, 0, 1), color), lines);
                    addCancellingLine(new Line(minPosition.add(1, 0, 1), minPosition.add(1, 0, 0), color), lines);
                    addCancellingLine(new Line(minPosition.add(1, 0, 1), minPosition.add(0, 0, 1), color), lines);
                }
                case UP -> {
                    addCancellingLine(new Line(minPosition.add(0, 1, 0), minPosition.add(1, 1, 0), color), lines);
                    addCancellingLine(new Line(minPosition.add(0, 1, 0), minPosition.add(0, 1, 1), color), lines);
                    addCancellingLine(new Line(maxPosition, minPosition.add(1, 1, 0), color), lines);
                    addCancellingLine(new Line(maxPosition, minPosition.add(0, 1, 1), color), lines);
                }
                case NORTH -> {
                    addCancellingLine(new Line(minPosition, minPosition.add(1, 0, 0), color), lines);
                    addCancellingLine(new Line(minPosition, minPosition.add(0, 1, 0), color), lines);
                    addCancellingLine(new Line(minPosition.add(1, 1, 0), minPosition.add(1, 0, 0), color), lines);
                    addCancellingLine(new Line(minPosition.add(1, 1, 0), minPosition.add(0, 1, 0), color), lines);
                }
                case SOUTH -> {
                    addCancellingLine(new Line(minPosition.add(0, 0, 1), minPosition.add(1, 0, 1), color), lines);
                    addCancellingLine(new Line(minPosition.add(0, 0, 1), minPosition.add(0, 1, 1), color), lines);
                    addCancellingLine(new Line(maxPosition, minPosition.add(1, 0, 1), color), lines);
                    addCancellingLine(new Line(maxPosition, minPosition.add(0, 1, 1), color), lines);
                }
                case WEST -> {
                    addCancellingLine(new Line(minPosition, minPosition.add(0, 1, 0), color), lines);
                    addCancellingLine(new Line(minPosition, minPosition.add(0, 0, 1), color), lines);
                    addCancellingLine(new Line(minPosition.add(0, 1, 1), minPosition.add(0, 0, 1), color), lines);
                    addCancellingLine(new Line(minPosition.add(0, 1, 1), minPosition.add(0, 1, 0), color), lines);
                }
                case EAST -> {
                    addCancellingLine(new Line(minPosition.add(1, 0, 0), minPosition.add(1, 1, 0), color), lines);
                    addCancellingLine(new Line(minPosition.add(1, 0, 0), minPosition.add(1, 0, 1), color), lines);
                    addCancellingLine(new Line(maxPosition, minPosition.add(1, 1, 0), color), lines);
                    addCancellingLine(new Line(maxPosition, minPosition.add(1, 0, 1), color), lines);
                }
            }
        });
        drawLines(lines, new RenderKey.CancellingFaces(color, direction));
    }

    public static void clear() {
        renders.clear();
    }

    private static void addCancellingLine(Line line, Set<Line> lines) {
        if (!lines.add(line)) {
            lines.remove(line);
        }
    }

    public static void registerEvents() {
        LevelExtractionEvents.END_EXTRACTION.register(RenderManager::extractLines);
        LevelRenderEvents.END_MAIN.register(RenderManager::renderLines);
    }

    private static void extractLines(LevelExtractionContext levelExtractionContext) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        levelExtractionContext.levelState().setData(LINES_SET_KEY, renders.entrySet().stream().filter(render -> {
            ChunkPos chunkPos = render.getKey().chunk();
            return level.getChunk(chunkPos.x(), chunkPos.z(), ChunkStatus.FULL, false) != null;
        }).toList());
    }

    private static void renderLines(LevelRenderContext context) {
        List<Map.Entry<RenderKey, FwickingWender>> extractedLines = context.levelState().getData(LINES_SET_KEY);
        if (extractedLines == null) {
            return;
        }

        int maxIndexCount = extractedLines.stream().mapToInt(entry -> entry.getValue().indexCount()).max().orElseThrow();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "", context.gameRenderer().mainRenderTarget().getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(NoDepthLayer.LINES_NO_DEPTH_PIPELINE)); // todo

            RenderSystem.bindDefaultUniforms(pass);
            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
            pass.setUniform("DynamicTransforms", dynamicTransforms);

            RenderSystem.AutoStorageIndexBuffer sequentialIndices = RenderSystem.getSequentialBuffer(NoDepthLayer.LINES_NO_DEPTH_PIPELINE.getPrimitiveTopology());
            GpuBuffer indexBuffer = sequentialIndices.getBuffer(maxIndexCount);
            pass.setIndexBuffer(indexBuffer, sequentialIndices.type());

            Vec3 cameraPos = context.levelState().cameraRenderState.pos;
            extractedLines.forEach(render -> {
                pass.setUniform("OffsetPosition", OffsetPosUniforms.write(
                    (float) (render.getKey().chunk().getMinBlockX() - cameraPos.x),
                    (float) -cameraPos.y,
                    (float) (render.getKey().chunk().getMinBlockZ() - cameraPos.z)
                ));

                pass.setVertexBuffer(0, render.getValue().theBuffer().slice());
                pass.drawIndexed(render.getValue().indexCount(), 1, 0, 0, 0);
            });
        }

        OffsetPosUniforms.endFrame();
    }
}
