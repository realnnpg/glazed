package com.nnpg.glazed.modules.main;

import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public class PearlMethod extends Module {
  private static final long LOOK_UP_DURATION_NANOS = 500_000_000L;
  private static final long SELECT_PEARL_DELAY_NANOS = 500_000_000L;
  private static final long THROW_PEARL_DELAY_NANOS = 500_000_000L;
  private static final long RTP_DELAY_NANOS = 100_000_000L;

  private Step step = Step.DONE;
  private long stepStartedAt;
  private float initialPitch;

  public PearlMethod() {
    super(GlazedAddon.CATEGORY, "pearl-method", "Looks up, throws an ender pearl, then runs /rtp.");
  }

  @Override
  public void onActivate() {
    this.step = Step.LOOK_UP;
    this.stepStartedAt = 0;
  }

  @Override
  public void onDeactivate() {
    this.step = Step.DONE;
    this.stepStartedAt = 0;
  }

  @EventHandler
  private void onTick(Post event) {
    if (this.step == Step.DONE) return;

    if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null
        || this.mc.player.connection == null) {
      this.toggle();
      return;
    }

    if (this.step == Step.WAIT_SELECT_PEARL || this.step == Step.WAIT_THROW) {
      this.mc.player.setXRot(-90.0F);
    }

    switch (this.step) {
      case LOOK_UP:
        this.initialPitch = this.mc.player.getXRot();
        this.startStep(Step.RAISE_CAMERA);
        break;

      case RAISE_CAMERA:
        double progress = Math.min(1.0,
            (double) (System.nanoTime() - this.stepStartedAt) / LOOK_UP_DURATION_NANOS);
        double easedProgress = progress * progress * (3.0 - 2.0 * progress);
        this.mc.player.setXRot((float) (this.initialPitch + (-90.0 - this.initialPitch) * easedProgress));
        if (progress >= 1.0) this.startStep(Step.WAIT_SELECT_PEARL);
        break;

      case WAIT_SELECT_PEARL:
        if (!this.delayDone(SELECT_PEARL_DELAY_NANOS)) break;

        int pearlSlot = this.findPearlSlot();
        if (pearlSlot == -1) {
          this.error("No ender pearl in the hotbar.");
          this.toggle();
          return;
        }

        if (!InvUtils.swap(pearlSlot, false)) {
          this.error("Could not select the ender pearl.");
          this.toggle();
          return;
        }

        this.startStep(Step.WAIT_THROW);
        break;

      case WAIT_THROW:
        if (!this.delayDone(THROW_PEARL_DELAY_NANOS)) break;

        if (!this.mc.player.getMainHandItem().is(Items.ENDER_PEARL)) {
          this.error("The ender pearl is no longer in your main hand.");
          this.toggle();
          return;
        }

        if (!this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND).consumesAction()) {
          this.error("Could not throw the ender pearl. Check its cooldown.");
          this.toggle();
          return;
        }

        this.mc.player.swing(InteractionHand.MAIN_HAND);
        this.startStep(Step.WAIT_RTP);
        break;

      case WAIT_RTP:
        if (!this.delayDone(RTP_DELAY_NANOS)) break;

        this.mc.player.connection.sendCommand("rtp");
        this.toggle();
        break;

      case DONE:
        break;
    }
  }

  private int findPearlSlot() {
    for (int slot = 0; slot < 9; slot++) {
      if (this.mc.player.getInventory().getItem(slot).is(Items.ENDER_PEARL)) return slot;
    }
    return -1;
  }

  private void startStep(Step nextStep) {
    this.step = nextStep;
    this.stepStartedAt = System.nanoTime();
  }

  private boolean delayDone(long delayNanos) {
    return System.nanoTime() - this.stepStartedAt >= delayNanos;
  }

  private enum Step {
    LOOK_UP,
    RAISE_CAMERA,
    WAIT_SELECT_PEARL,
    WAIT_THROW,
    WAIT_RTP,
    DONE
  }
}
