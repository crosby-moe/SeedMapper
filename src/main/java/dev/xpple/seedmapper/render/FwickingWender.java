package dev.xpple.seedmapper.render;

import com.mojang.renderpearl.api.buffers.GpuBuffer;

public record FwickingWender(GpuBuffer theBuffer, int indexCount) {
    FwickingWender(GpuBuffer theBuffer) {
        this(theBuffer, NoDepthLayer.LINES_NO_DEPTH_PIPELINE.getPrimitiveTopology().indexCount((int) (theBuffer.size() / NoDepthLayer.LINES_NO_DEPTH_PIPELINE.getVertexFormatBinding(0).getVertexSize())));
    }

    public void close() {
        this.theBuffer().close();
    }
}
