package com.nnpg.glazed.utils.glazed;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public class RotationUtil {

    public enum CurveType {
        SIGMOID,
        ACCELERATION,
        LINEAR
    }

    public static class RotationConfig {
        final CurveType curve;
        final float maxDegreesPerTick;
        final float yawAccelMin;
        final float yawAccelMax;
        final float pitchAccelMin;
        final float pitchAccelMax;
        final float sigmoidSteepness;
        final float sigmoidMidpoint;
        final float yawAccelError;
        final float pitchAccelError;
        final float yawConstError;
        final float pitchConstError;
        final float inputBlendWeight;
        final float resetThreshold;

        private RotationConfig(Builder b) {
            this.curve             = b.curve;
            this.maxDegreesPerTick = b.maxDegreesPerTick;
            this.yawAccelMin       = b.yawAccelMin;
            this.yawAccelMax       = b.yawAccelMax;
            this.pitchAccelMin     = b.pitchAccelMin;
            this.pitchAccelMax     = b.pitchAccelMax;
            this.sigmoidSteepness  = b.sigmoidSteepness;
            this.sigmoidMidpoint   = b.sigmoidMidpoint;
            this.yawAccelError     = b.yawAccelError;
            this.pitchAccelError   = b.pitchAccelError;
            this.yawConstError     = b.yawConstError;
            this.pitchConstError   = b.pitchConstError;
            this.inputBlendWeight  = MathHelper.clamp(b.inputBlendWeight, 0f, 1f);
            this.resetThreshold    = b.resetThreshold;
        }

        public static class Builder {
            private CurveType curve          = CurveType.ACCELERATION;
            private float maxDegreesPerTick  = 30.0f;
            private float yawAccelMin        = 20.0f;
            private float yawAccelMax        = 25.0f;
            private float pitchAccelMin      = 20.0f;
            private float pitchAccelMax      = 25.0f;
            private float sigmoidSteepness   = 10.0f;
            private float sigmoidMidpoint    = 0.3f;
            private float yawAccelError      = 0.1f;
            private float pitchAccelError    = 0.1f;
            private float yawConstError      = 0.1f;
            private float pitchConstError    = 0.1f;
            private float inputBlendWeight   = 0.0f;
            private float resetThreshold     = 0.5f;

            public Builder curve(CurveType v)           { this.curve = v;            return this; }
            public Builder maxDegreesPerTick(float v)   { this.maxDegreesPerTick = v; return this; }
            public Builder yawAccel(float min, float max)   { yawAccelMin = min; yawAccelMax = max; return this; }
            public Builder pitchAccel(float min, float max) { pitchAccelMin = min; pitchAccelMax = max; return this; }
            public Builder sigmoidSteepness(float v)    { this.sigmoidSteepness = v; return this; }
            public Builder sigmoidMidpoint(float v)     { this.sigmoidMidpoint = v;  return this; }
            public Builder yawAccelError(float v)       { this.yawAccelError = v;    return this; }
            public Builder pitchAccelError(float v)     { this.pitchAccelError = v;  return this; }
            public Builder yawConstError(float v)       { this.yawConstError = v;    return this; }
            public Builder pitchConstError(float v)     { this.pitchConstError = v;  return this; }
            public Builder inputBlendWeight(float v)    { this.inputBlendWeight = v; return this; }
            public Builder resetThreshold(float v)      { this.resetThreshold = v;   return this; }
            public RotationConfig build()               { return new RotationConfig(this); }
        }
    }

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private boolean active          = false;
    private float   targetYaw       = 0f;
    private float   targetPitch     = 0f;
    private float   currentYaw      = 0f;
    private float   currentPitch    = 0f;
    private float   previousYaw     = 0f;
    private float   previousPitch   = 0f;
    private float   prevMouseYaw    = 0f;
    private float   prevMousePitch  = 0f;

    private RotationConfig config;

    public void start(float targetYaw, float targetPitch, RotationConfig config) {
        if (mc.player == null) return;
        this.config        = config;
        this.targetYaw     = MathHelper.wrapDegrees(targetYaw);
        this.targetPitch   = MathHelper.clamp(targetPitch, -90f, 90f);
        this.currentYaw    = mc.player.getYaw();
        this.currentPitch  = mc.player.getPitch();
        this.previousYaw   = this.currentYaw;
        this.previousPitch = this.currentPitch;
        this.prevMouseYaw  = this.currentYaw;
        this.prevMousePitch = this.currentPitch;
        this.active        = true;
    }

    public boolean tick() {
        if (!active || mc.player == null) return true;

        float remainingYaw   = angleDiff(currentYaw, targetYaw);
        float remainingPitch = targetPitch - currentPitch;
        float geometricAngle = (float) Math.sqrt(remainingYaw * remainingYaw + remainingPitch * remainingPitch);

        if (geometricAngle <= config.resetThreshold) {
            applyRotationWithFixedYaw(targetYaw, targetPitch);
            active = false;
            return true;
        }

        float rawMouseYaw    = mc.player.getYaw();
        float rawMousePitch  = mc.player.getPitch();
        float mouseDeltaYaw   = angleDiff(prevMouseYaw,   rawMouseYaw);
        float mouseDeltaPitch = rawMousePitch - prevMousePitch;
        prevMouseYaw   = rawMouseYaw;
        prevMousePitch = rawMousePitch;

        float prevDeltaYaw   = angleDiff(previousYaw,   currentYaw);
        float prevDeltaPitch = currentPitch - previousPitch;

        float newDeltaYaw;
        float newDeltaPitch;

        switch (config.curve) {
            case ACCELERATION -> {
                newDeltaYaw   = computeAccel(remainingYaw,   prevDeltaYaw,   geometricAngle, true);
                newDeltaPitch = computeAccel(remainingPitch, prevDeltaPitch, geometricAngle, false);
            }
            case SIGMOID -> {
                float factor  = sigmoidFactor(geometricAngle);
                float speedYaw   = randomRange(config.yawAccelMin,   config.yawAccelMax)   * factor;
                float speedPitch = randomRange(config.pitchAccelMin, config.pitchAccelMax) * factor;
                newDeltaYaw   = Math.abs(remainingYaw)   > 0 ? MathHelper.clamp(remainingYaw,   -speedYaw,   speedYaw)   : 0f;
                newDeltaPitch = Math.abs(remainingPitch) > 0 ? MathHelper.clamp(remainingPitch, -speedPitch, speedPitch) : 0f;
            }
            default -> {
                float speedYaw   = Math.min(config.maxDegreesPerTick, Math.abs(remainingYaw));
                float speedPitch = Math.min(config.maxDegreesPerTick, Math.abs(remainingPitch));
                newDeltaYaw   = Math.signum(remainingYaw)   * speedYaw;
                newDeltaPitch = Math.signum(remainingPitch) * speedPitch;
            }
        }

        newDeltaYaw   = MathHelper.clamp(newDeltaYaw,   -config.maxDegreesPerTick, config.maxDegreesPerTick);
        newDeltaPitch = MathHelper.clamp(newDeltaPitch, -config.maxDegreesPerTick, config.maxDegreesPerTick);

        newDeltaYaw   += errorForAxis(newDeltaYaw,   config.yawAccelError,   config.yawConstError);
        newDeltaPitch += errorForAxis(newDeltaPitch, config.pitchAccelError, config.pitchConstError);

        if (config.inputBlendWeight > 0f) {
            float w       = config.inputBlendWeight;
            newDeltaYaw   = newDeltaYaw   * (1f - w) + mouseDeltaYaw   * w;
            newDeltaPitch = newDeltaPitch * (1f - w) + mouseDeltaPitch * w;
        }

        double gcd    = computeGcd();
        newDeltaYaw   = snapToGcd(newDeltaYaw,   gcd);
        newDeltaPitch = snapToGcd(newDeltaPitch, gcd);

        previousYaw   = currentYaw;
        previousPitch = currentPitch;

        float newYaw   = currentYaw   + newDeltaYaw;
        float newPitch = MathHelper.clamp(currentPitch + newDeltaPitch, -90f, 90f);

        applyRotation(newYaw, newPitch);

        return false;
    }

    public boolean isActive()           { return active; }
    public float   getCurrentYaw()      { return currentYaw; }
    public float   getCurrentPitch()    { return currentPitch; }

    public void cancel() {
        if (!active || mc.player == null) { active = false; return; }
        applyRotationWithFixedYaw(currentYaw, currentPitch);
        active = false;
    }

    /**
     * Correct the velocity vector for movement packets when rotation is active.
     * Call this in the movement input handler of the module using this util.
     * Returns corrected movement input velocity using the rotated yaw.
     */
    public Vec3d correctMovement(Vec3d inputVelocity, float speed) {
        if (!active || mc.player == null) return inputVelocity;
        float yaw    = (float) Math.toRadians(currentYaw);
        double sin   = Math.sin(yaw);
        double cos   = Math.cos(yaw);
        double x     = inputVelocity.x * cos - inputVelocity.z * sin;
        double z     = inputVelocity.x * sin + inputVelocity.z * cos;
        return new Vec3d(x, inputVelocity.y, z).normalize().multiply(speed);
    }

    private void applyRotation(float yaw, float pitch) {
        if (mc.player == null) return;
        currentYaw   = yaw;
        currentPitch = pitch;
        mc.player.prevYaw     = mc.player.getYaw();
        mc.player.prevPitch   = mc.player.getPitch();
        mc.player.bodyYaw     = yaw;
        mc.player.prevBodyYaw = yaw;
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    /**
     * On cancel/reset: smoothly fix yaw drift rather than snapping.
     * Mirrors LiquidBounce's withFixedYaw: rotation.yaw + angleDiff(player.yRot, rotation.yaw)
     */
    private void applyRotationWithFixedYaw(float yaw, float pitch) {
        if (mc.player == null) return;
        float fixedYaw = yaw + angleDiff(mc.player.getYaw(), yaw);
        applyRotation(fixedYaw, pitch);
    }

    private float computeAccel(float remaining, float prevDelta, float geometricAngle, boolean isYaw) {
        float accelMin = isYaw ? config.yawAccelMin   : config.pitchAccelMin;
        float accelMax = isYaw ? config.yawAccelMax   : config.pitchAccelMax;
        float range    = randomRange(accelMin, accelMax);
        float decFactor = sigmoidFactor(geometricAngle);
        float accel    = MathHelper.wrapDegrees(remaining - prevDelta);
        accel          = MathHelper.clamp(accel, -range, range) * decFactor;
        return prevDelta + accel;
    }

    private float sigmoidFactor(float geometricAngle) {
        float scaled   = geometricAngle / 120f;
        double sigmoid = 1.0 / (1.0 + Math.exp(-config.sigmoidSteepness * (scaled - config.sigmoidMidpoint)));
        return (float) MathHelper.clamp(sigmoid, 0.0, 1.0);
    }

    private float errorForAxis(float delta, float accelErr, float constErr) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        return delta * (float)(rng.nextDouble() * 2.0 - 1.0) * accelErr
             + (float)(rng.nextDouble() * 2.0 - 1.0) * constErr;
    }

    private float randomRange(float min, float max) {
        if (min >= max) return min;
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }

    private float snapToGcd(float delta, double gcd) {
        if (gcd <= 0.0) return delta;
        return (float)(Math.round(delta / gcd) * gcd);
    }

    private double computeGcd() {
        if (mc.options == null) return 0.0;
        double s = mc.options.getMouseSensitivity().getValue();
        double f = s * 0.6 + 0.2;
        return f * f * f * 8.0 * 0.15;
    }

    private float angleDiff(float from, float to) {
        return MathHelper.wrapDegrees(to - from);
    }
}
