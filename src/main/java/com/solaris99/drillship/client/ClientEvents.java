package com.solaris99.drillship.client;

import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.network.ModNetwork;
import com.solaris99.drillship.ship.ShipShape;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

public final class ClientEvents {
    // The field of view the world was last drawn with, so the HUD can put marks where things are on screen.
    private static double fov = 70.0;

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        DriveControls.register(event);
    }

    public static void register(IEventBus bus) {
        bus.addListener(ClientEvents::onClientTick);
        bus.addListener(DriveControls::onMovementInput);
        bus.addListener(ClientEvents::onRenderPlayer);
        bus.addListener(ClientEvents::onRenderHand);
        bus.addListener(ClientEvents::onRenderBlockOverlay);
        bus.addListener(ClientEvents::onCameraDistance);
        bus.addListener(ClientEvents::onEntityJoin);
        bus.addListener(ClientEvents::onComputeFov);
    }

    static double fov() {
        return fov;
    }

    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) {
            fov = event.getFOV();
        }
    }

    public static boolean isDriving(LocalPlayer player) {
        return DriveControls.isDriving(player);
    }

    // Runs before vanilla handles key presses, so the drive keys can hold back vanilla actions on the same keys.
    private static void onClientTick(ClientTickEvent.Pre event) {
        RadioSounds.tick();
        Minecraft minecraft = Minecraft.getInstance();
        if (!isDriving(minecraft.player)) {
            ModNetwork.ShipStatus.latest = null;
            ModNetwork.RadarScan.latest = null;
        }
        DriveControls.tick(minecraft);
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

    // The driver's eye is kept clear of the blocks being drilled; this covers what slips through (e.g. a block placed
    // into the ship), so the view is never replaced by a block's texture.
    private static void onRenderBlockOverlay(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK && event.getPlayer().getVehicle() instanceof DrillshipEntity) {
            event.setCanceled(true);
        }
    }

    // In third person, back off far enough to see the whole drillhead instead of sitting inside its hull.
    private static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (event.getCamera().getEntity().getVehicle() instanceof DrillshipEntity ship) {
            ShipShape shape = ship.shape();
            event.setDistance((float) (shape.hullLength() + shape.height * 1.5));
        }
    }

    private ClientEvents() {
    }
}
