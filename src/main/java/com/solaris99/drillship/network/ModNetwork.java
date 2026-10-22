package com.solaris99.drillship.network;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipState;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(DriveInput.TYPE, DriveInput.CODEC, ModNetwork::handleDriveInput);
        registrar.playToServer(ToggleDrill.TYPE, ToggleDrill.CODEC, ModNetwork::handleToggleDrill);
    }

    /** The driver's steering keys, sent whenever they change. */
    public record DriveInput(float forward, float left, boolean up, boolean down) implements CustomPacketPayload {
        public static final Type<DriveInput> TYPE = new Type<>(Drillships.id("drive_input"));
        public static final StreamCodec<ByteBuf, DriveInput> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, DriveInput::forward,
                ByteBufCodecs.FLOAT, DriveInput::left,
                ByteBufCodecs.BOOL, DriveInput::up,
                ByteBufCodecs.BOOL, DriveInput::down,
                DriveInput::new);

        @Override
        public Type<DriveInput> type() {
            return TYPE;
        }
    }

    public record ToggleDrill() implements CustomPacketPayload {
        public static final ToggleDrill INSTANCE = new ToggleDrill();
        public static final Type<ToggleDrill> TYPE = new Type<>(Drillships.id("toggle_drill"));
        public static final StreamCodec<ByteBuf, ToggleDrill> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<ToggleDrill> type() {
            return TYPE;
        }
    }

    private static void handleDriveInput(DriveInput input, IPayloadContext context) {
        if (context.player().getVehicle() instanceof DrillshipEntity ship) {
            ship.setInput(input.forward(), input.left(), input.up(), input.down());
        }
    }

    private static void handleToggleDrill(ToggleDrill toggle, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof DrillshipEntity entity) {
            ShipState ship = ShipManager.get(player.server).get(entity.getUUID());
            if (ship != null) {
                ship.setDrillOn(!ship.isDrillOn());
            }
        }
    }

    private ModNetwork() {
    }
}
