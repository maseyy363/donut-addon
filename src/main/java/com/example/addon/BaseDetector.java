package com.example.addon.modules;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared base-detection logic.
 * - Only counts storage that is UNDERGROUND (below the surface by at least `depth` blocks).
 * - Weights rare/player-only blocks (ender chests, shulkers, hoppers, beacons...) much higher than plain chests.
 * - Looks at each chunk AND its 8 neighbours together, so a base that straddles chunk borders is
 *   still found, and a cluster of chunks is reported once (at its strongest chunk), not 9 times.
 */
public final class BaseDetector {
    private BaseDetector() {}

    public static final class Result {
        public double score;
        public double region;
        public int storage;
        public int types;
        public int playerSections;
        public BlockPos center;
        public final Set<Class<?>> kinds = new HashSet<>();
    }

    public static Result analyze(WorldChunk chunk, int depth, int sectionWeight) {
        Result r = new Result();
        long sx = 0, sy = 0, sz = 0;

        for (BlockEntity be : chunk.getBlockEntities().values()) {
            double w = PlayerBlocks.weight(be);
            if (w <= 0) continue;
            BlockPos p = be.getPos();
            int surface = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, p.getX(), p.getZ());
            if (p.getY() > surface - depth) continue; // not underground enough
            r.score += w;
            r.storage++;
            r.kinds.add(be.getClass());
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
        r.types = r.kinds.size();
        r.center = r.storage > 0
            ? new BlockPos((int) (sx / r.storage), (int) (sy / r.storage), (int) (sz / r.storage))
            : new BlockPos(cxm, Math.max(chunk.getBottomY(), surfaceMid - depth - 8), czm);
        return r;
    }

    /**
     * Scans every loaded chunk around (cx, cz) and returns the strongest chunk of each base cluster.
     * A cluster counts when the chunk plus its neighbours reach the score / type / storage minimums
     * and the chunk itself carries a real share of it.
     */
    public static Map<ChunkPos, Result> findBases(ClientWorld world, int cx, int cz, int vd, int depth,
                                                 int sectionWeight, int minScore, int minTypes, int minStorage) {
        Map<ChunkPos, Result> all = new HashMap<>();
        for (int x = cx - vd; x <= cx + vd; x++) {
            for (int z = cz - vd; z <= cz + vd; z++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(x, z);
                if (chunk == null) continue;
                Result r = analyze(chunk, depth, sectionWeight);
                if (r.score > 0) all.put(chunk.getPos(), r);
            }
        }

        Map<ChunkPos, Result> flagged = new HashMap<>();
        for (Map.Entry<ChunkPos, Result> e : all.entrySet()) {
            ChunkPos p = e.getKey();
            Result r = e.getValue();
            double region = r.score;
            int storage = r.storage;
            Set<Class<?>> kinds = new HashSet<>(r.kinds);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    Result n = all.get(new ChunkPos(p.x + dx, p.z + dz));
                    if (n == null) continue;
                    region += n.score;
                    storage += n.storage;
                    kinds.addAll(n.kinds);
                }
            }
            if (region >= minScore && kinds.size() >= minTypes && storage >= minStorage
                && r.score >= minScore * 0.35) {
                r.region = region;
                flagged.put(p, r);
            }
        }

        // Keep only the strongest chunk of each cluster so one base = one marker.
        Map<ChunkPos, Result> out = new HashMap<>();
        for (Map.Entry<ChunkPos, Result> e : flagged.entrySet()) {
            ChunkPos p = e.getKey();
            Result r = e.getValue();
            boolean best = true;
            for (int dx = -1; dx <= 1 && best; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    ChunkPos np = new ChunkPos(p.x + dx, p.z + dz);
                    Result n = flagged.get(np);
                    if (n == null) continue;
                    if (n.score > r.score
                        || (n.score == r.score && (np.x < p.x || (np.x == p.x && np.z < p.z)))) {
                        best = false;
                        break;
                    }
                }
            }
            if (best) out.put(p, r);
        }
        return out;
    }

    public static String confidence(double region, int minScore) {
        double ratio = region / Math.max(1, minScore);
        return ratio >= 3 ? "HIGH" : ratio >= 1.8 ? "MEDIUM" : "LOW";
    }
}
