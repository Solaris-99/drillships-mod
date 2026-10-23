package com.solaris99.drillship.network;

import javax.annotation.Nullable;

import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipState;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(DriveInput.TYPE, DriveInput.CODEC, ModNetwork::handleDriveInput);
        registrar.playToServer(ToggleDrill.TYPE, ToggleDrill.CODEC, ModNetwork::handleToggleDrill);
        registrar.playToClient(ShipStatus.TYPE, ShipStatus.CODEC, (status, context) -> ShipStatus.latest = status);
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

    /** What the driver's HUD shows that the client can't see on the entity. Sent to the driver a few times a second. */
    public record ShipStatus(int energy, int capacity, boolean drillOn, int drillDamage, int drillMax, int warnings, float speed)
            implements CustomPacketPayload {
        public static final Type<ShipStatus> TYPE = new Type<>(Drillships.id("ship_status"));
        public static final StreamCodec<FriendlyByteBuf, ShipStatus> CODEC = StreamCodec.ofMember(ShipStatus::write, ShipStatus::read);

        /** The last status received, read by the client's HUD. */
        @Nullable
        public static volatile ShipStatus latest;

        public static ShipStatus of(ShipState ship, long gameTime) {
            ItemStack drill = ship.drill();
            return new ShipStatus(ship.energy(), ship.tier().energyCapacity(), ship.isDrillOn(),
                    drill.isEmpty() ? 0 : drill.getDamageValue(), drill.isEmpty() ? 0 : drill.getMaxDamage(),
                    ship.warnings(gameTime), (float) ship.speed());
        }

        private static ShipStatus read(FriendlyByteBuf buf) {
            return new ShipStatus(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readFloat());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(energy);
            buf.writeVarInt(capacity);
            buf.writeBoolean(drillOn);
            buf.writeVarInt(drillDamage);
            buf.writeVarInt(drillMax);
            buf.writeVarInt(warnings);
            buf.writeFloat(speed);
        }

        @Override
        public Type<ShipStatus> type() {
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
