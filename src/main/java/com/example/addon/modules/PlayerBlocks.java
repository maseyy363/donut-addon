package com.example.addon.modules;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;

import java.util.Set;

/** Shared helpers: what counts as "player-made" or "storage". */
public final class PlayerBlocks {
    private PlayerBlocks() {}

    /** Blocks that basically never generate naturally in bulk. */
    public static final Set<Block> PLAYER_MADE = Set.of(
        Blocks.CRAFTING_TABLE, Blocks.GLASS, Blocks.GLASS_PANE, Blocks.TINTED_GLASS,
        Blocks.BEACON, Blocks.ENCHANTING_TABLE, Blocks.ANVIL, Blocks.TNT, Blocks.HOPPER,
        Blocks.DROPPER, Blocks.DISPENSER, Blocks.OBSERVER, Blocks.PISTON, Blocks.STICKY_PISTON,
        Blocks.REDSTONE_LAMP, Blocks.COMPARATOR, Blocks.REPEATER, Blocks.LECTERN,
        Blocks.BREWING_STAND, Blocks.COMPOSTER, Blocks.END_ROD, Blocks.SEA_LANTERN,
        Blocks.RESPAWN_ANCHOR, Blocks.BOOKSHELF, Blocks.NOTE_BLOCK, Blocks.JUKEBOX,
        Blocks.FURNACE, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.LANTERN
    );

    public static boolean isStorage(BlockEntity be) {
        return be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
            || be instanceof ShulkerBoxBlockEntity || be instanceof FurnaceBlockEntity
            || be instanceof BlastFurnaceBlockEntity || be instanceof SmokerBlockEntity
            || be instanceof HopperBlockEntity || be instanceof BedBlockEntity
            || be instanceof EnderChestBlockEntity || be instanceof DispenserBlockEntity
            || be instanceof CrafterBlockEntity;
    }
}
