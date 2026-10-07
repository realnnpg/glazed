package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;

public class AutoRelog extends Module {
  private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
  private final Setting<Integer> height =
      this.sgGeneral.add(
          new IntSetting.Builder()
              .name("height")
              .description("Reconnect when your Y coordinate is at or below this height.")
              .defaultValue(-2)
              .sliderRange(-64, 320)
              .build());

  private ServerData pendingServer;

  public AutoRelog() {
    super(GlazedAddon.CATEGORY, "auto-relog", "Disconnects at the selected height and immediately reconnects.");
    this.runInMainMenu = true;
  }

  @Override
  public void onActivate() {
    this.pendingServer = null;
  }

  @Override
  public void onDeactivate() {
    this.pendingServer = null;
  }

  @EventHandler
  private void onTick(Post event) {
    if (!this.isActive()) return;

    if (this.pendingServer != null) {
      this.reconnectIfReady();
      return;
    }

    if (this.mc.player == null || this.mc.level == null || this.mc.player.connection == null) return;
    if (this.mc.player.getY() > this.height.get()) return;

    ServerData server = this.mc.getCurrentServer();
    if (server == null || server.ip == null || server.ip.isBlank()) {
      this.error("Cannot reconnect: no multiplayer server address is available.");
      this.toggle();
      return;
    }

    this.pendingServer = server;
    this.mc.player.connection.handleDisconnect(
        new ClientboundDisconnectPacket(Component.literal("AutoRelog: reached Y " + this.height.get())));
    this.reconnectIfReady();
  }

  private void reconnectIfReady() {
    if (this.pendingServer == null || this.mc.player != null || this.mc.level != null) return;

    ServerData server = this.pendingServer;
    this.toggle();
    ConnectScreen.startConnecting(
        new TitleScreen(), this.mc, ServerAddress.parseString(server.ip), server, false, null);
  }
}
