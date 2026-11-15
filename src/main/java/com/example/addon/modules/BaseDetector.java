package com.example.addon.modules;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared base-detection logic.
 * - Only counts storage that is UNDERGROUND (below the surface by at least `depth` blocks).
 * - Weights rare/player-only blocks (ender chests, shulkers, hoppers...) much higher than plain chests,
 *   so a lone dungeon / temple / buried-treasure chest never scores high.
 * - Counts how many different kinds of storage there are (natural structures only have 1-2 kinds).
 */
public final class BaseDetector {
    private BaseDetector() {}

    public static final class Result {
        public double score;
        public int storage;
        public int types;
        public int playerSections;
        public BlockPos center;
    }

    public static Result analyze(WorldChunk chunk, int depth, int sectionWeight) {
        Result r = new Result();
        Set<Class<?>> kinds = new HashSet<>();
        long sx = 0, sy = 0, sz = 0;

        for (BlockEntity be : chunk.getBlockEntities().values()) {
            double w = PlayerBlocks.weight(be);
            if (w <= 0) continue;
            BlockPos p = be.getPos();
            int surface = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, p.getX(), p.getZ());
            if (p.getY() > surface - depth) continue; // not underground enough
            r.score += w;
            r.storage++;
            kinds.add(be.getClass());
            sx += p.getX(); sy += p.getY(); sz += p.getZ();
        }

        int cxm = chunk.getPos().getStartX() + 8, czm = chunk.getPos().getStartZ() + 8;
        int surfaceMid = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, cxm, czm);
        ChunkSection[] sections = chunk.getSectionArray();
        for (int i = 0; i < sections.length; i++) {
            ChunkSection s = sections[i];
            if (s == null || s.isEmpty()) continue;
            int baseY = (chunk.getBottomSectionCoord() + i) * 16;
            if (baseY + 15 > surfaceMid - depth) continue; // section reaches the surface
            if (s.hasAny(st -> PlayerBlocks.PLAYER_MADE.contains(st.getBlock()))) r.playerSections++;
        }

        r.score += r.playerSections * (double) sectionWeight;
        r.types = kinds.size();
        r.center = r.storage > 0
            ? new BlockPos((int) (sx / r.storage), (int) (sy / r.storage), (int) (sz / r.storage))
            : new BlockPos(cxm, Math.max(chunk.getBottomY(), surfaceMid - depth - 8), czm);
        return r;
    }
}
