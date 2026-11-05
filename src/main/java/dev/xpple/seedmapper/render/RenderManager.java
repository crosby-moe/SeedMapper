package dev.xpple.seedmapper.render;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.util.BufferedRegionPos;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

public final class RenderManager {

    private RenderManager() {
    }

    public static final boolean RANDOMIZE_COLORS_PER_BUFFER = true;
    private static final RenderStateDataKey<Collection<FwickingWender>> LINES_SET_KEY = RenderStateDataKey.create(() -> "SeedMapper renders");

    // I LOVE STATE MANAGEMENT I LOVE STATE MANAGEMENT I LOVE STATE MANAGEMENT
    private static Map<RenderKey, LineBatch> theLines = Collections.emptyMap(); // dude if this fucks up let's crash asap on some real shit
    private static final Map<BufferedRegionPos, FwickingWender> renders = new Object2ObjectOpenHashMap<>();

    private static final Queue<LineBatch> removalQueue = new ConcurrentLinkedQueue<>();

    static {
        rebuildRenderMap();
    }

    public static void rebuildRenderMap() {
        Map<RenderKey, LineBatch> temp = theLines;
        theLines = CacheBuilder.newBuilder()
            .expireAfterWrite(Configs.HighlightDuration)
            .<RenderKey, LineBatch>removalListener(notif -> {
                if (notif.getCause() != RemovalCause.EXPLICIT) removalQueue.add(notif.getValue()); // avoid closing gpu buffers when rebuilding render map
            })
            .build().asMap();
        theLines.putAll(temp);
        temp.clear();
    }

    private static void drawLines(Collection<Line> lines, RenderKey.RenderKind renderKind) {
        // first split lines into per buffered region line batches
        lines.stream().collect(Collectors.groupingBy(line -> BufferedRegionPos.containing(line.start())))
                .entrySet().stream().map(entry -> new LineBatch(entry.getKey(), entry.getValue()))

        // then convert to buffered region local coordinates
            .map(lineBatch -> new LineBatch(
                lineBatch.regionPos(),
                lineBatch.lines().stream().map(line -> line.subtract(lineBatch.regionPos().getLowerCorner())).toList()
            ))

        // then add line batches to the cache for tracking expiration
            .peek(lineBatch -> {
                RenderKey renderKey = new RenderKey(lineBatch.regionPos(), renderKind);
                @Nullable LineBatch oldValue = theLines.put(renderKey, lineBatch);
                // expire old batch, cant use removal queue here, or it may remove ^ new value above
                if (oldValue != null) {
                    @Nullable FwickingWender wender = renders.get(oldValue.regionPos());
                    if (wender != null) wender.depend(oldValue);
                }
            })

        // then add line batches to be rendered
            .forEach(lineBatch -> {
                FwickingWender wender = renders.computeIfAbsent(lineBatch.regionPos(), FwickingWender::new);
                wender.append(lineBatch);
            });
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
        renders.values().forEach(FwickingWender::close);
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

        // process removal queue (guava cache is prone to race condition i think)
        LineBatch removal;
        while ((removal = removalQueue.poll()) != null) {
            @Nullable FwickingWender wender = renders.get(removal.regionPos());
            if (wender != null) wender.depend(removal);
        }

        // upload da new thingies :3
        renders.values().forEach(FwickingWender::ratioBozo);

        // wipe da empty ones >w<
        renders.values().removeIf(FwickingWender::closeIfEmpty);

        // set da render thingie
        levelExtractionContext.levelState().setData(LINES_SET_KEY, renders.values());
    }

    private static void renderLines(LevelRenderContext context) {
        Collection<FwickingWender> wenders = context.levelState().getData(LINES_SET_KEY);
        if (wenders == null || wenders.isEmpty()) {
            return;
        }

        int maxIndexCount = wenders.stream().mapToInt(FwickingWender::getIndexCount).max().orElseThrow();

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "SeedMapper renders", context.gameRenderer().mainRenderTarget().getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(NoDepthLayer.LINES_NO_DEPTH_PIPELINE));

            RenderSystem.bindDefaultUniforms(pass);
            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
            pass.setUniform("DynamicTransforms", dynamicTransforms);

            RenderSystem.AutoStorageIndexBuffer sequentialIndices = RenderSystem.getSequentialBuffer(NoDepthLayer.LINES_NO_DEPTH_PIPELINE.getPrimitiveTopology());
            GpuBuffer indexBuffer = sequentialIndices.getBuffer(maxIndexCount);
            pass.setIndexBuffer(indexBuffer, sequentialIndices.type());

            Vec3 cameraPos = context.levelState().cameraRenderState.pos;
            wenders.forEach(render -> {
                pass.setUniform("OffsetPosition", OffsetPosUniforms.write(
                    (float) (render.regionPos.getMinBlockX() - cameraPos.x),
                    (float) -cameraPos.y,
                    (float) (render.regionPos.getMinBlockZ() - cameraPos.z)
                ));

                pass.setVertexBuffer(0, render.getVertexBuffer());
                pass.drawIndexed(render.getIndexCount(), 1, 0, 0, 0);
            });
        }

        OffsetPosUniforms.endFrame();
    }
}
