package com.solaris99.drillship.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.network.ModNetwork;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientEvents {
    // Only active while driving, so it may share F with "swap offhand" without a conflict warning.
    private static final IKeyConflictContext DRIVING = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive() && isDriving(Minecraft.getInstance().player);
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return this == other;
        }
    };

    public static final KeyMapping TOGGLE_DRILL = new KeyMapping("key.drillships.toggle_drill", DRIVING,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F, "key.categories.drillships");

    // Resend input at least this often, in case a packet was missed.
    private static final int RESEND_TICKS = 20;

    private static ModNetwork.DriveInput lastInput;
    private static int ticksSinceSent;

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_DRILL);
    }

    public static void register(IEventBus bus) {
        bus.addListener(ClientEvents::onClientTick);
        bus.addListener(ClientEvents::onRenderPlayer);
        bus.addListener(ClientEvents::onRenderHand);
        bus.addListener(ClientEvents::onEntityJoin);
    }

    public static boolean isDriving(LocalPlayer player) {
        return player != null && player.getVehicle() instanceof DrillshipEntity;
    }

    // Runs before vanilla handles key presses, so F doesn't also swap hands while driving.
    private static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!isDriving(player)) {
            lastInput = null;
            return;
        }
        while (TOGGLE_DRILL.consumeClick()) {
            PacketDistributor.sendToServer(ModNetwork.ToggleDrill.INSTANCE);
        }
        if (TOGGLE_DRILL.getKey().equals(minecraft.options.keySwapOffhand.getKey())) {
            while (minecraft.options.keySwapOffhand.consumeClick()) {
                // Swallowed: the key toggles the drill instead.
            }
        }

        Input input = player.input;
        ModNetwork.DriveInput current = new ModNetwork.DriveInput(input.forwardImpulse, input.leftImpulse, input.jumping,
                minecraft.options.keySprint.isDown());
        ticksSinceSent++;
        if (!current.equals(lastInput) || ticksSinceSent >= RESEND_TICKS) {
            PacketDistributor.sendToServer(current);
            lastInput = current;
            ticksSinceSent = 0;
        }
    }

    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof DrillshipEntity ship) {
            DrillshipSoundInstance.startAll(ship);
        }
    }

    private static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity().getVehicle() instanceof DrillshipEntity) {
            event.setCanceled(true);
        }
    }

    private static void onRenderHand(RenderHandEvent event) {
        if (isDriving(Minecraft.getInstance().player)) {
            event.setCanceled(true);
        }
    }

    private ClientEvents() {
    }
}
