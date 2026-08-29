package com.nnpg.glazed.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;

import java.util.Optional;

public class DeathPosCommand extends Command {
    public DeathPosCommand() {
        super("deathpos", "Prints the last death location the server sent you, and copies it.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                error("Join a server first.");
                return SINGLE_SUCCESS;
            }

            Optional<GlobalPos> death = mc.player.getLastDeathLocation();

            if (death.isEmpty()) {
                error("The server never sent a last death location. Nothing to show.");
                return SINGLE_SUCCESS;
            }

            BlockPos pos = death.get().pos();
            String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();

            info("Last death: (highlight)%s(default) in %s", coords, death.get().dimension().identifier());
            mc.keyboardHandler.setClipboard(coords);
            info("Copied to your clipboard.");

            return SINGLE_SUCCESS;
        });
    }
}
