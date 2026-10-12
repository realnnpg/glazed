package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import java.util.Set;

public class NoBlockInteract extends Module {

    public NoBlockInteract() {
        super(GlazedAddon.CATEGORY, "no-block-interact", "Lets you pearl through containers by blocking GUI interactions but still throwing pearls.");
    }

    // listed one by one cus theres no tag for it, i looked
    private final Set<Block> blockedBlocks = Set.of(
        Blocks.CHEST,
        Blocks.ENDER_CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.CRAFTING_TABLE,
        Blocks.ENCHANTING_TABLE,
        Blocks.ANVIL,
        Blocks.LECTERN,
        Blocks.BARREL,
        Blocks.SMITHING_TABLE,
        Blocks.FURNACE,
        Blocks.BLAST_FURNACE,
        Blocks.SMOKER,
        Blocks.HOPPER,
        Blocks.DISPENSER,
        Blocks.DROPPER,
        Blocks.BREWING_STAND,
        Blocks.BEACON,
        Blocks.GRINDSTONE,
        Blocks.LOOM,
        Blocks.STONECUTTER,
        Blocks.CARTOGRAPHY_TABLE,
        Blocks.FLETCHING_TABLE,
        Blocks.CHISELED_BOOKSHELF,
        Blocks.DECORATED_POT,
        Blocks.CRAFTER,
        Blocks.SHULKER_BOX,
        Blocks.DYED_SHULKER_BOX.white(),
        Blocks.DYED_SHULKER_BOX.orange(),
        Blocks.DYED_SHULKER_BOX.magenta(),
        Blocks.DYED_SHULKER_BOX.lightBlue(),
        Blocks.DYED_SHULKER_BOX.yellow(),
        Blocks.DYED_SHULKER_BOX.lime(),
        Blocks.DYED_SHULKER_BOX.pink(),
        Blocks.DYED_SHULKER_BOX.gray(),
        Blocks.DYED_SHULKER_BOX.lightGray(),
        Blocks.DYED_SHULKER_BOX.cyan(),
        Blocks.DYED_SHULKER_BOX.purple(),
        Blocks.DYED_SHULKER_BOX.blue(),
        Blocks.DYED_SHULKER_BOX.brown(),
        Blocks.DYED_SHULKER_BOX.green(),
        Blocks.DYED_SHULKER_BOX.red(),
        Blocks.DYED_SHULKER_BOX.black()
    );

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (event.packet instanceof ServerboundUseItemOnPacket packet) {
            BlockPos pos = packet.getHitResult().getBlockPos();
            Block block = mc.level.getBlockState(pos).getBlock();

            if (!blockedBlocks.contains(block)) return;

            if (mc.player.getMainHandItem().getItem() == Items.ENDER_PEARL) {
                event.cancel();
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
            else if (mc.player.getOffhandItem().getItem() == Items.ENDER_PEARL) {
                event.cancel();
                mc.gameMode.useItem(mc.player, InteractionHand.OFF_HAND);
                mc.player.swing(InteractionHand.OFF_HAND);
            }
            else {
                event.cancel();
                mc.player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }
}
