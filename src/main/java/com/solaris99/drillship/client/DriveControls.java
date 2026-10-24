package com.solaris99.drillship.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.network.ModNetwork;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The drillship's controls: key mappings of their own (keys or mouse buttons), live only while driving, so they can
 * share keys with anything vanilla does on foot. While driving, the vanilla actions on those keys are held back: a
 * click bound to the drill doesn't also attack. Steering isn't a key: the ship turns toward where the driver looks. Set in
 * the controls panel (DriveControlsScreen) or in vanilla's key binds.
 */
public final class DriveControls {
    // Only active while driving, so these may share keys with vanilla's. It counts as in-game: NeoForge treats a key
    // with no modifier as up while Shift, Ctrl or Alt is held unless its context conflicts with IN_GAME, which would
    // make the default on Shift (leave) never register.
    static final IKeyConflictContext DRIVING = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive() && isDriving(Minecraft.getInstance().player);
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return this == other || other == KeyConflictContext.IN_GAME;
        }
    };

    public static final KeyMapping FORWARD = key("drive_forward", GLFW.GLFW_KEY_W);
    public static final KeyMapping BACK = key("drive_back", GLFW.GLFW_KEY_S);
    public static final KeyMapping TOGGLE_DRILL = key("toggle_drill", GLFW.GLFW_KEY_F);
    public static final KeyMapping RADAR_TARGET = key("radar_target", GLFW.GLFW_KEY_R);
    public static final KeyMapping LEAVE = key("leave_helm", GLFW.GLFW_KEY_LEFT_SHIFT);

    /** In the order the panel lists them. */
    public static final List<KeyMapping> ALL = List.of(FORWARD, BACK, TOGGLE_DRILL, RADAR_TARGET, LEAVE);

    // Resend input at least this often, in case a packet was missed.
    private static final int RESEND_TICKS = 20;

    private static ModNetwork.DriveInput lastInput;
    private static int ticksSinceSent;
    private static boolean drillHeld;

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.drillships." + name, DRIVING, InputConstants.Type.KEYSYM, key, "key.categories.drillships");
    }

    public static void register(RegisterKeyMappingsEvent event) {
        ALL.forEach(event::register);
    }

    public static boolean isDriving(LocalPlayer player) {
        return player != null && player.getVehicle() instanceof DrillshipEntity;
    }

    /** Each client tick, before vanilla handles its keys. */
    static void tick(Minecraft minecraft) {
        if (!isDriving(minecraft.player)) {
            lastInput = null;
            drillHeld = false;
            return;
        }
        holdBackVanilla(minecraft);
        if (ClientConfig.DRILL_MODE.get() == ClientConfig.DrillMode.HOLD) {
            TOGGLE_DRILL.consumeClick();
            if (TOGGLE_DRILL.isDown() != drillHeld) {
                drillHeld = TOGGLE_DRILL.isDown();
                PacketDistributor.sendToServer(new ModNetwork.SetDrill(drillHeld));
            }
        } else {
            while (TOGGLE_DRILL.consumeClick()) {
                PacketDistributor.sendToServer(ModNetwork.ToggleDrill.INSTANCE);
            }
        }
        while (RADAR_TARGET.consumeClick()) {
            PacketDistributor.sendToServer(new ModNetwork.CycleRadarTarget(1));
        }
        ModNetwork.DriveInput current = new ModNetwork.DriveInput(axis(FORWARD, BACK));
        ticksSinceSent++;
        if (!current.equals(lastInput) || ticksSinceSent >= RESEND_TICKS) {
            PacketDistributor.sendToServer(current);
            lastInput = current;
            ticksSinceSent = 0;
        }
    }

    // Vanilla mappings on the same keys as ours do nothing while driving: no attacking, using, swapping hands...
    private static void holdBackVanilla(Minecraft minecraft) {
        for (KeyMapping vanilla : minecraft.options.keyMappings) {
            if (ALL.contains(vanilla)) {
                continue;
            }
            for (KeyMapping ours : ALL) {
                if (!ours.isUnbound() && vanilla.same(ours)) {
                    while (vanilla.consumeClick()) {
                        // Swallowed: the key drives the ship instead.
                    }
                    vanilla.setDown(false);
                    break;
                }
            }
        }
    }

    /**
     * The player's own movement while driving: none, and "sneaking" (which is what leaves the helm, on the server)
     * only when the leave key is down, whatever key sneaking is bound to.
     */
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !isDriving(player)) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = LEAVE.isDown();
    }

    private static float axis(KeyMapping positive, KeyMapping negative) {
        return (positive.isDown() ? 1 : 0) - (negative.isDown() ? 1 : 0);
    }

    private DriveControls() {
    }
}
