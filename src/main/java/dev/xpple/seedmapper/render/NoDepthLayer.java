package dev.xpple.seedmapper.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.*;
import dev.xpple.seedmapper.SeedMapper;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class NoDepthLayer {
    private NoDepthLayer() {
    }

    private static final BindGroupLayout OFFSET_POSITION = BindGroupLayout.builder().withUniform("OffsetPosition", UniformType.UNIFORM_BUFFER).build();

    public static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withVertexShader(Identifier.fromNamespaceAndPath(SeedMapper.MOD_ID, "lines/rendertype_buffered_lines"))
            .withFragmentShader("core/rendertype_lines")
            .withLocation(Identifier.fromNamespaceAndPath(SeedMapper.MOD_ID, "pipeline/lines_no_depth"))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
            .withPrimitiveTopology(PrimitiveTopology.LINES)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withBindGroupLayout(OFFSET_POSITION)
            .build()
    );
}
