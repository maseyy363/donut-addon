package com.example.addon.modules;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;

import java.util.Set;

/** Shared helpers: what counts as "player-made" and how much each storage block is worth. */
public final class PlayerBlocks {
    private PlayerBlocks() {}

    /** Blocks that basically never generate naturally in bulk underground. */
    public static final Set<Block> PLAYER_MADE = Set.of(
        Blocks.CRAFTING_TABLE, Blocks.GLASS, Blocks.GLASS_PANE, Blocks.TINTED_GLASS,
        Blocks.BEACON, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.TNT, Blocks.HOPPER,
        Blocks.DROPPER, Blocks.DISPENSER, Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON,
        Blocks.REDSTONE_LAMP, Blocks.COMPARATOR, Blocks.REPEATER, Blocks.LECTERN,
        Blocks.BREWING_STAND, Blocks.COMPOSTER, Blocks.END_ROD, Blocks.SEA_LANTERN,
        Blocks.RESPAWN_ANCHOR, Blocks.BOOKSHELF, Blocks.NOTE_BLOCK, Blocks.JUKEBOX,
        Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.LANTERN
    );

    /**
     * Points per storage block. Plain chests are worth very little because natural
     * structures (dungeons, temples, bastions, ancient cities...) are full of them.
     * Things that don't generate naturally are worth a lot.
     */
    public static double weight(BlockEntity be) {
        if (be instanceof EnderChestBlockEntity) return 6;
        if (be instanceof ShulkerBoxBlockEntity) return 4;
        if (be instanceof HopperBlockEntity) return 3;
        if (be instanceof CrafterBlockEntity) return 3;
        if (be instanceof DispenserBlockEntity) return 2;
        if (be instanceof BarrelBlockEntity) return 1.5;
        if (be instanceof FurnaceBlockEntity || be instanceof BlastFurnaceBlockEntity
            || be instanceof SmokerBlockEntity) return 1.5;
        if (be instanceof ChestBlockEntity) return 1;
        if (be instanceof BedBlockEntity) return 0.5;
        return 0;
    }

    public static boolean isStorage(BlockEntity be) { return weight(be) > 0; }
}
