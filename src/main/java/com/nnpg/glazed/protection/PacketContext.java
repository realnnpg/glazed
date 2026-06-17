package com.nnpg.glazed.protection;

import net.minecraft.network.packet.Packet;

public class PacketContext {
    private static final ThreadLocal<Boolean> PROCESSING_PACKET =
        ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<String> PACKET_NAME =
        ThreadLocal.withInitial(() -> "unknown");

    private PacketContext() {}

    public static boolean isProcessingPacket() {
        return PROCESSING_PACKET.get();
    }

    public static void setProcessingPacket(boolean value) {
        PROCESSING_PACKET.set(value);
    }

    public static String getPacketName() {
        return PACKET_NAME.get();
    }

    public static void setPacketName(Object packet) {
        if (packet instanceof Packet<?> p) {
            try {

                String name = p.getPacketType().toString();
                PACKET_NAME.set(name != null ? name : p.getClass().getSimpleName());
            } catch (Exception e) {
                PACKET_NAME.set(p.getClass().getSimpleName());
            }
        }
    }
}
