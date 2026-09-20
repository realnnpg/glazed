package com.nnpg.glazed.modules.main;

import com.mojang.blaze3d.platform.InputConstants;
import com.nnpg.glazed.GlazedAddon;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.meteor.MouseScrollEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.ChunkOcclusionEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.CameraType;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

public class GlazedFreecam extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Move freely.")
        .defaultValue(0.8)
        .min(0.05)
        .max(20.0)
        .sliderRange(0.05, 20.0)
        .build()
    );

    private final Setting<Boolean> staySneaking = sgGeneral.add(new BoolSetting.Builder()
        .name("stay-sneaking")
        .description("If you were already sneaking when you enabled freecam, keeps you sneaking so you stay on the ground and mine underwater faster.")
        .defaultValue(true)
        .build()
    );

    public final Vector3d currentPosition = new Vector3d();
    public final Vector3d previousPosition = new Vector3d();

    public float yaw;
    public float pitch;
    public float previousYaw;
    public float previousPitch;

    private CameraType currentPerspective;
    private double savedFovEffect;
    private boolean savedBobView;

    private boolean isMovingForward;
    private boolean isMovingBackward;
    private boolean isMovingRight;
    private boolean isMovingLeft;
    private boolean isMovingUp;
    private boolean isMovingDown;

    private boolean sneakOnEnable;

    private long lastFrameTime;

    public GlazedFreecam() {
        super(GlazedAddon.CATEGORY, "glazed-freecam", "Move freely.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.level == null || mc.options == null) {
            toggle();
            return;
        }

        savedFovEffect = mc.options.fovEffectScale().get();
        savedBobView = mc.options.bobView().get();
        mc.options.fovEffectScale().set(0.0);
        mc.options.bobView().set(false);

        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        currentPerspective = mc.options.getCameraType();

        Vec3 eyePos = mc.player.getEyePosition();
        currentPosition.set(eyePos.x, eyePos.y, eyePos.z);
        previousPosition.set(eyePos.x, eyePos.y, eyePos.z);

        // vro
        mc.player.setDeltaMovement(0.0, 0.0, 0.0);

        if (currentPerspective == CameraType.THIRD_PERSON_FRONT) {
            yaw += 180.0f;
            pitch *= -1.0f;
        }

        mc.options.setCameraType(CameraType.FIRST_PERSON);

        previousYaw = yaw;
        previousPitch = pitch;

        isMovingForward = mc.options.keyUp.isDown();
        isMovingBackward = mc.options.keyDown.isDown();
        isMovingRight = mc.options.keyRight.isDown();
        isMovingLeft = mc.options.keyLeft.isDown();
        isMovingUp = mc.options.keyJump.isDown();
        isMovingDown = mc.options.keyShift.isDown();

        sneakOnEnable = mc.player.isShiftKeyDown();

        lastFrameTime = System.currentTimeMillis();
        resetMovementKeys();

        if (mc.levelRenderer != null) mc.levelExtractor.allChanged();
    }

    @Override
    public void onDeactivate() {
        restoreView();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        restoreView();
        toggle();
    }

    @EventHandler
    private void onRespawn(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundRespawnPacket)) return;

        restoreView();
        toggle();
    }

    private void restoreView() {
        resetMovementKeys();

        sneakOnEnable = false;

        previousPosition.set(currentPosition);
        previousYaw = yaw;
        previousPitch = pitch;

        if (mc.levelRenderer != null) mc.execute(mc.levelExtractor::allChanged);

        if (mc.options == null) return;

        mc.options.fovEffectScale().set(savedFovEffect);
        mc.options.bobView().set(savedBobView);

        mc.options.setCameraType(currentPerspective != null ? currentPerspective : CameraType.FIRST_PERSON);
    }

    // sus
    @EventHandler
    private void onChunkOcclusion(ChunkOcclusionEvent event) {
        event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.options == null) return;
        resetMovementKeys();

        if (staySneaking.get() && sneakOnEnable) mc.options.keyShift.setDown(true);

        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.FIRST_PERSON);
    }

    public void onGameRender() {
        if (mc.options == null) return;

        pollMovementKeys();

        previousPosition.set(currentPosition);
        previousYaw = yaw;
        previousPitch = pitch;

        long currentTime = System.currentTimeMillis();
        float deltaTime = (currentTime - lastFrameTime) / 1000.0f;
        lastFrameTime = currentTime;

        if (deltaTime > 0.1f) deltaTime = 0.1f;
        if (deltaTime < 0.001f) deltaTime = 0.016f;

        Vec3 forward = Vec3.directionFromRotation(0.0f, yaw);
        Vec3 right = Vec3.directionFromRotation(0.0f, yaw + 90.0f);

        double moveX = 0, moveY = 0, moveZ = 0;
        double moveSpeed = speed.get() * 2 * (isBoundDown(mc.options.keySprint) ? 2.0 : 1.0);

        if (isMovingForward) {
            moveX += forward.x * moveSpeed;
            moveZ += forward.z * moveSpeed;
        }
        if (isMovingBackward) {
            moveX -= forward.x * moveSpeed;
            moveZ -= forward.z * moveSpeed;
        }
        if (isMovingRight) {
            moveX += right.x * moveSpeed;
            moveZ += right.z * moveSpeed;
        }
        if (isMovingLeft) {
            moveX -= right.x * moveSpeed;
            moveZ -= right.z * moveSpeed;
        }
        if (isMovingUp) moveY += moveSpeed;
        if (isMovingDown) moveY -= moveSpeed;

        currentPosition.x += moveX * deltaTime * 5.0;
        currentPosition.y += moveY * deltaTime * 5.0;
        currentPosition.z += moveZ * deltaTime * 5.0;
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onMouseScroll(MouseScrollEvent event) {
        if (event.value == 0 || mc.gui.screen() != null) return;

        adjustSpeed(event.value > 0 ? 1 : -1);
        event.cancel();
    }

    public void adjustSpeed(int dir) {
        double step = Math.max(0.25, speed.get() * 0.15);
        speed.set(Math.max(0.05, Math.min(20.0, speed.get() + dir * step)));
    }

    public void updateRotation(double deltaYaw, double deltaPitch) {
        yaw += (float) deltaYaw;
        pitch += (float) deltaPitch;

        yaw = Mth.wrapDegrees(yaw);
        pitch = Mth.clamp(pitch, -90.0f, 90.0f);
    }

    // ong
    public double getInterpolatedX(float partialTicks) {
        return currentPosition.x;
    }

    public double getInterpolatedY(float partialTicks) {
        return currentPosition.y;
    }

    public double getInterpolatedZ(float partialTicks) {
        return currentPosition.z;
    }

    public double getInterpolatedYaw(float partialTicks) {
        return yaw;
    }

    public double getInterpolatedPitch(float partialTicks) {
        return pitch;
    }

    private void resetMovementKeys() {
        if (mc.options == null) return;

        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
    }

    private void pollMovementKeys() {
        boolean active = mc.gui.screen() == null && !isKeyPressed(GLFW.GLFW_KEY_F3);

        isMovingForward = active && isBoundDown(mc.options.keyUp);
        isMovingBackward = active && isBoundDown(mc.options.keyDown);
        isMovingRight = active && isBoundDown(mc.options.keyRight);
        isMovingLeft = active && isBoundDown(mc.options.keyLeft);
        isMovingUp = active && isBoundDown(mc.options.keyJump);
        isMovingDown = active && isBoundDown(mc.options.keyShift);
    }

    private boolean isBoundDown(KeyMapping bind) {
        if (bind == null || mc.getWindow() == null) return false;

        try {
            InputConstants.Key key = InputConstants.getKey(bind.saveString());
            if (key == null || key.equals(InputConstants.UNKNOWN)) return false;

            long window = mc.getWindow().handle();
            if (key.getType() == InputConstants.Type.MOUSE) {
                return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            }
            return GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isKeyPressed(int key) {
        return mc.getWindow() != null && GLFW.glfwGetKey(mc.getWindow().handle(), key) == GLFW.GLFW_PRESS;
    }
}
