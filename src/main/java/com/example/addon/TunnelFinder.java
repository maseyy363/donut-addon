package com.example.addon.modules;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds player-dug tunnels and shafts in the chunk data the server already sent you.
 *
 * Natural caves are wide and curvy. A hand-dug tunnel is a perfectly straight corridor that is
 * 1 block wide and 1-2 blocks high with solid walls, floor and ceiling, and a dug shaft is a
 * straight 1x1 vertical hole. Long straight runs like that are very unlikely to be natural.
 *
 * Chunks are scanned a few at a time (nearest first) and cached, so it does not lag. Purely passive.
 */
public final class TunnelFinder {
    private TunnelFinder() {}

    /** Shortest run that is ever recorded. */
    public static final int MIN_RUN = 6;

    private static final byte OTHER = 0, PASS = 1, WALL = 2;
    private static final int MAX_SEGMENTS_PER_CHUNK = 300;

    public static final class Segment {
        public final int x1, y1, z1, x2, y2, z2, length;
        public final boolean vertical;

        Segment(int x1, int y1, int z1, int x2, int y2, int z2, int length, boolean vertical) {
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
            this.length = length;
            this.vertical = vertical;
        }
    }

    public static final class Result {
        public final List<Segment> segments = new ArrayList<>();

        /** Total length of the runs that are at least minRun long. */
        public int blocks(int minRun) {
            int n = 0;
            for (Segment s : segments) if (s.length >= minRun) n += s.length;
            return n;
        }
    }

    private static final Map<ChunkPos, Result> cache = new HashMap<>();
    private static final Set<ChunkPos> done = new HashSet<>();
    private static ClientWorld lastWorld;
    private static int sinceRescan;
    private static long lastRun;

    public static Map<ChunkPos, Result> all() { return cache; }

    public static int blocks(ChunkPos p, int minRun) {
        Result r = cache.get(p);
        return r == null ? 0 : r.blocks(minRun);
    }

    /**
     * Call once per tick. Scans up to `budget` not-yet-scanned chunks (nearest first) and
     * starts a fresh pass every `rescanTicks`. Safe to call from several modules in the same tick.
     */
    public static void tick(ClientWorld world, int cx, int cz, int vd, int budget, int rescanTicks) {
        long now = System.currentTimeMillis();
        if (now - lastRun < 25) return; // another module already ran this tick
        lastRun = now;

        if (world != lastWorld) {
            cache.clear();
            done.clear();
            lastWorld = world;
            sinceRescan = 0;
        }
        if (++sinceRescan >= rescanTicks) {
            sinceRescan = 0;
            done.clear();
        }
        cache.keySet().removeIf(p -> Math.abs(p.x - cx) > vd + 1 || Math.abs(p.z - cz) > vd + 1);

        int left = budget;
        for (int r = 0; r <= vd && left > 0; r++) {
            for (int dx = -r; dx <= r && left > 0; dx++) {
                for (int dz = -r; dz <= r && left > 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    ChunkPos cp = new ChunkPos(cx + dx, cz + dz);
                    if (done.contains(cp)) continue;
                    Result res = scan(world, cp);
                    if (res == null) continue; // not loaded yet, try again later
                    done.add(cp);
                    cache.put(cp, res);
                    left--;
                }
            }
        }
    }

    private static byte classify(ClientWorld world, BlockState st, BlockPos pos) {
        if (st.isAir()) return PASS;
        if (!st.getFluidState().isEmpty()) return OTHER;
        if (st.isOf(Blocks.LADDER)) return PASS;
        if (st.getCollisionShape(world, pos).isEmpty()) return PASS; // torches, rails, signs, buttons...
        return WALL;
    }

    private static boolean pass(byte[][][] c, int yLo, int x, int y, int z) { return c[x + 1][y - yLo][z + 1] == PASS; }
    private static boolean wall(byte[][][] c, int yLo, int x, int y, int z) { return c[x + 1][y - yLo][z + 1] == WALL; }

    /** Cell of a corridor running along X: 1 wide (Z), 1 or 2 high, solid floor / ceiling / sides. */
    private static boolean validX(byte[][][] c, int yLo, int x, int y, int z) {
        if (!pass(c, yLo, x, y, z)) return false;
        if (!wall(c, yLo, x, y - 1, z)) return false;
        if (!wall(c, yLo, x, y, z - 1) || !wall(c, yLo, x, y, z + 1)) return false;
        if (wall(c, yLo, x, y + 1, z)) return true; // crawl space
        return pass(c, yLo, x, y + 1, z) && wall(c, yLo, x, y + 2, z)
            && wall(c, yLo, x, y + 1, z - 1) && wall(c, yLo, x, y + 1, z + 1);
    }

    /** Cell of a corridor running along Z: 1 wide (X). */
    private static boolean validZ(byte[][][] c, int yLo, int x, int y, int z) {
        if (!pass(c, yLo, x, y, z)) return false;
        if (!wall(c, yLo, x, y - 1, z)) return false;
        if (!wall(c, yLo, x - 1, y, z) || !wall(c, yLo, x + 1, y, z)) return false;
        if (wall(c, yLo, x, y + 1, z)) return true;
        return pass(c, yLo, x, y + 1, z) && wall(c, yLo, x, y + 2, z)
            && wall(c, yLo, x - 1, y + 1, z) && wall(c, yLo, x + 1, y + 1, z);
    }

    /** Cell of a 1x1 vertical shaft: passable with solid blocks on all four sides. */
    private static boolean shaft(byte[][][] c, int yLo, int x, int y, int z) {
        return pass(c, yLo, x, y, z)
            && wall(c, yLo, x - 1, y, z) && wall(c, yLo, x + 1, y, z)
            && wall(c, yLo, x, y, z - 1) && wall(c, yLo, x, y, z + 1);
    }

    private static void add(Result res, int x1, int y1, int z1, int x2, int y2, int z2, int len, boolean vertical) {
        if (res.segments.size() >= MAX_SEGMENTS_PER_CHUNK) return;
        res.segments.add(new Segment(x1, y1, z1, x2, y2, z2, len, vertical));
    }

    private static Result scan(ClientWorld world, ChunkPos cp) {
        WorldChunk chunk = world.getChunkManager().getWorldChunk(cp.x, cp.z);
        if (chunk == null) return null;

        int minY = Math.max(world.getBottomY() + 1, -63);
        int maxY = Math.min(world.getTopY() - 3, 120);
        Result res = new Result();
        if (maxY <= minY) return res;

        // Cheap skip: no air at all in this height range means nothing was dug here.
        boolean anyAir = false;
        ChunkSection[] secs = chunk.getSectionArray();
        for (int i = 0; i < secs.length && !anyAir; i++) {
            int baseY = (chunk.getBottomSectionCoord() + i) * 16;
            if (baseY > maxY || baseY + 15 < minY) continue;
            ChunkSection s = secs[i];
            if (s != null && s.hasAny(st -> st.isAir())) anyAir = true;
        }
        if (!anyAir) return res;

        // Classify the chunk plus a 1-block border: 0 = other (fluid), 1 = open, 2 = solid.
        int yLo = minY - 1, yHi = maxY + 2, h = yHi - yLo + 1;
        byte[][][] c = new byte[18][h][18];
        BlockPos.Mutable mp = new BlockPos.Mutable();
        int sx = cp.getStartX(), sz = cp.getStartZ();
        for (int dx = -1; dx <= 16; dx++) {
            for (int dz = -1; dz <= 16; dz++) {
                for (int y = yLo; y <= yHi; y++) {
                    mp.set(sx + dx, y, sz + dz);
                    c[dx + 1][y - yLo][dz + 1] = classify(world, world.getBlockState(mp), mp);
                }
            }
        }

        for (int y = minY; y <= maxY; y++) {
            // Corridors along X
            for (int z = 0; z < 16; z++) {
                int run = 0;
                for (int x = 0; x <= 16; x++) {
                    boolean ok = x < 16 && validX(c, yLo, x, y, z);
                    if (ok) { run++; continue; }
                    if (run >= MIN_RUN) {
                        int height = pass(c, yLo, x - run, y + 1, z) ? 2 : 1;
                        add(res, sx + x - run, y, sz + z, sx + x, y + height, sz + z + 1, run, false);
                    }
                    run = 0;
                }
            }
            // Corridors along Z
            for (int x = 0; x < 16; x++) {
                int run = 0;
                for (int z = 0; z <= 16; z++) {
                    boolean ok = z < 16 && validZ(c, yLo, x, y, z);
                    if (ok) { run++; continue; }
                    if (run >= MIN_RUN) {
                        int height = pass(c, yLo, x, y + 1, z - run) ? 2 : 1;
                        add(res, sx + x, y, sz + z - run, sx + x + 1, y + height, sz + z, run, false);
                    }
                    run = 0;
                }
            }
        }

        // Vertical shafts
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int run = 0;
                for (int y = minY; y <= maxY + 1; y++) {
                    boolean ok = y <= maxY && shaft(c, yLo, x, y, z);
                    if (ok) { run++; continue; }
                    if (run >= MIN_RUN) add(res, sx + x, y - run, sz + z, sx + x + 1, y, sz + z + 1, run, true);
                    run = 0;
                }
            }
        }
        return res;
    }
}
