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
        registrar.playToServer(SetDrill.TYPE, SetDrill.CODEC, ModNetwork::handleSetDrill);
        registrar.playToServer(CycleRadarTarget.TYPE, CycleRadarTarget.CODEC, ModNetwork::handleCycleRadarTarget);
        registrar.playToClient(ShipStatus.TYPE, ShipStatus.CODEC, (status, context) -> ShipStatus.latest = status);
        registrar.playToClient(RadarScan.TYPE, RadarScan.CODEC, (scan, context) -> {
            RadarScan.latest = scan;
            RadarScan.receivedAt = System.currentTimeMillis();
        });
    }

    /**
     * The radar's picture for the driver's HUD, measured from the drillhead's middle: its tier, ore range and target
     * (a RadarOre), the target's nearest deposits packed by {@link #find}, nearest first, and from tier 3 the contacts
     * around the ship packed by {@link #contact}, the water depth under it (-1 when not on water) and the distance to
     * the shore along its heading (-1 when none is within range).
     */
    public record RadarScan(int tier, int oreRange, int target, int[] finds, int[] contacts, int depth, int shore)
            implements CustomPacketPayload {
        public static final Type<RadarScan> TYPE = new Type<>(Drillships.id("radar_scan"));
        public static final StreamCodec<FriendlyByteBuf, RadarScan> CODEC = StreamCodec.ofMember(RadarScan::write, RadarScan::read);

        /** The last scan received and when (System.currentTimeMillis()), read by the client's HUD. */
        @Nullable
        public static volatile RadarScan latest;
        public static volatile long receivedAt;

        public static int contact(int dx, int dz, int type) {
            return ((dx + 1024) & 0x7FF) << 13 | ((dz + 1024) & 0x7FF) << 2 | type;
        }

        public static int contactX(int contact) {
            return (contact >> 13 & 0x7FF) - 1024;
        }

        public static int contactZ(int contact) {
            return (contact >> 2 & 0x7FF) - 1024;
        }

        public static int contactType(int contact) {
            return contact & 3;
        }

        /** Offsets within +-127 blocks and how many ore blocks (up to 255) are in the group. */
        public static int find(int dx, int dy, int dz, int count) {
            return (dx + 128 & 0xFF) << 24 | (dy + 128 & 0xFF) << 16 | (dz + 128 & 0xFF) << 8 | Math.min(255, count);
        }

        public static int findX(int find) {
            return (find >>> 24) - 128;
        }

        public static int findY(int find) {
            return (find >> 16 & 0xFF) - 128;
        }

        public static int findZ(int find) {
            return (find >> 8 & 0xFF) - 128;
        }

        public static int count(int find) {
            return find & 0xFF;
        }

        private static RadarScan read(FriendlyByteBuf buf) {
            return new RadarScan(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarIntArray(), buf.readVarIntArray(),
                    buf.readVarInt() - 1, buf.readVarInt() - 1);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(tier);
            buf.writeVarInt(oreRange);
            buf.writeVarInt(target);
            buf.writeVarIntArray(finds);
            buf.writeVarIntArray(contacts);
            buf.writeVarInt(depth + 1);
            buf.writeVarInt(shore + 1);
        }

        @Override
        public Type<RadarScan> type() {
            return TYPE;
        }
    }

    /** The driver's radar target key: the next kind of ore (or, with a negative step, the previous one). */
    public record CycleRadarTarget(int step) implements CustomPacketPayload {
        public static final Type<CycleRadarTarget> TYPE = new Type<>(Drillships.id("cycle_radar_target"));
        public static final StreamCodec<ByteBuf, CycleRadarTarget> CODEC = ByteBufCodecs.VAR_INT.map(CycleRadarTarget::new, CycleRadarTarget::step);

        @Override
        public Type<CycleRadarTarget> type() {
            return TYPE;
        }
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

    /** The drill key in hold mode: on while held, off when let go. */
    public record SetDrill(boolean on) implements CustomPacketPayload {
        public static final Type<SetDrill> TYPE = new Type<>(Drillships.id("set_drill"));
        public static final StreamCodec<ByteBuf, SetDrill> CODEC = ByteBufCodecs.BOOL.map(SetDrill::new, SetDrill::on);

        @Override
        public Type<SetDrill> type() {
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

    private static void handleSetDrill(SetDrill set, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof DrillshipEntity entity) {
            ShipState ship = ShipManager.get(player.server).get(entity.getUUID());
            if (ship != null) {
                ship.setDrillOn(set.on());
            }
        }
    }

    private static void handleCycleRadarTarget(CycleRadarTarget cycle, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.getVehicle() instanceof DrillshipEntity entity) {
            ShipState ship = ShipManager.get(player.server).get(entity.getUUID());
            if (ship != null && ship.radarTier() > 0) {
                ship.cycleRadarTarget(cycle.step());
            }
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
