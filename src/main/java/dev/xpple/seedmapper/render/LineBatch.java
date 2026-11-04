package dev.xpple.seedmapper.render;

import dev.xpple.seedmapper.util.BufferedRegionPos;

import java.util.List;

public record LineBatch(BufferedRegionPos regionPos, List<Line> lines) {
}
