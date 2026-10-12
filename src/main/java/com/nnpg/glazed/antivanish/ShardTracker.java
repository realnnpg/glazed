package com.nnpg.glazed.antivanish;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

public final class ShardTracker {
    private static final int OFF_MAP = Integer.MIN_VALUE;

    private ClientLevel level;
    private LocalPlayer self;
    private String brand;
    private int goliath = OFF_MAP;

    public boolean moved(Minecraft mc) {
        ClientLevel nowLevel = mc.level;
        LocalPlayer nowSelf = mc.player;

        if (nowLevel == null || nowSelf == null) {
            clear();
            return false;
        }

        ClientPacketListener connection = mc.getConnection();
        String nowBrand = connection == null ? null : connection.serverBrand();
        int nowGoliath = goliath(nowLevel, nowSelf);

        boolean moved = level != null && (
                nowLevel != level
                        || (nowSelf != self && !self.isDeadOrDying())
                        || nowGoliath != goliath
                        || (nowBrand != null && brand != null && !nowBrand.equals(brand))
        );

        level = nowLevel;
        self = nowSelf;
        brand = nowBrand;
        goliath = nowGoliath;

        return moved;
    }

    public void clear() {
        level = null;
        self = null;
        brand = null;
        goliath = OFF_MAP;
    }

    private static int goliath(ClientLevel level, LocalPlayer self) {
        if (level.dimension() != Level.OVERWORLD) return OFF_MAP;

        return GoliathMap.goliathAt(self.getX(), self.getZ());
    }
}
