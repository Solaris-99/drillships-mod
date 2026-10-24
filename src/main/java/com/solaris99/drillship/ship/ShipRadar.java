package com.solaris99.drillship.ship;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.solaris99.drillship.entity.ShipPartEntity;
import com.solaris99.drillship.entity.ShipSectionEntity;
import com.solaris99.drillship.network.ModNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The ship's radar, shown to the driver as a compass line on the HUD: the nearest deposits of the ore it is set to
 * look for (RadarOre), with their height. One radar, cumulative: each radar module installed raises its tier, up to
 * {@link #MAX_TIER}.
 * <ul>
 * <li>tier 1: metals, within 24 blocks, with whether they lie above or below;</li>
 * <li>tier 2: every ore, within 32 blocks, and the distance to them;</li>
 * <li>tier 3: within 48 blocks, the exact height, creatures and hostiles around the ship, and on water the depth
 * under it and the distance to the shore ahead.</li>
 * </ul>
 * Only loaded chunks are scanned; the radar never loads any. The ore scan reads every block of the sections around the
 * ship, so it is spread over ticks: a few sections a tick, and the finds of the last complete sweep are what the driver
 * sees. One instance per drillhead, server side.
 */
public final class ShipRadar {
    public static final int MAX_TIER = 3;
    // How far ores are found by tier, from the drillhead's middle; half that up and down.
    private static final int[] ORE_RANGE = {0, 24, 32, 48};
    public static final int CONTACT_RANGE = 48;
    public static final int SHORE_RANGE = 64;
    // Contacts.
    public static final int CREATURE = 0;
    public static final int VESSEL = 1;
    public static final int HOSTILE = 2;
    public static final int PLAYER = 3;
    // Water columns: land, lava, or water plus its depth.
    private static final int UNKNOWN = -3;
    private static final int LAND = -2;
    private static final int LAVA = -1;

    private static final int SEND_TICKS = 10;
    private static final int CONTACT_HEIGHT = 16;
    private static final int MAX_CONTACTS = 32;
    private static final int MAX_FINDS = 16;
    // Sections with the ore in them read per tick, about 50k blocks.
    private static final int SECTIONS_PER_TICK = 12;

    // The sweep in progress: sections still to read, around where it started, for one kind of ore.
    private final List<int[]> sections = new ArrayList<>();
    private final Map<Long, int[]> groups = new HashMap<>();
    private Map<Block, RadarOre> kinds = Map.of();
    private BlockPos sweepCenter = BlockPos.ZERO;
    private int sweepRange;
    private RadarOre sweepTarget = RadarOre.IRON;
    private int cursor;
    private int sweeps;
    // The last complete sweep.
    private List<int[]> finds = List.of();
    private BlockPos findsCenter = BlockPos.ZERO;
    private RadarOre findsTarget = RadarOre.IRON;

    /** The radar's tier with this many radar modules. */
    public static int tier(int radars) {
        return Math.min(MAX_TIER, radars);
    }

    /** How far a radar of this tier finds ores, in blocks. */
    public static int oreRange(int tier) {
        return ORE_RANGE[Math.max(0, Math.min(MAX_TIER, tier))];
    }

    /** Runs every tick while someone drives: advances the ore sweep and now and then sends the driver a picture. */
    public void tick(ServerLevel level, ShipState ship, ServerPlayer driver, long gameTime) {
        int tier = ship.radarTier();
        if (tier == 0) {
            sections.clear();
            finds = List.of();
            return;
        }
        BlockPos center = center(ship);
        RadarOre target = ship.radarTarget();
        sweep(level, center, oreRange(tier), target);
        if (gameTime % SEND_TICKS == 0 && driver.connection.hasChannel(ModNetwork.RadarScan.TYPE)) {
            PacketDistributor.sendToPlayer(driver, scan(level, ship, center, tier, target));
        }
    }

    // The drillhead's middle: what the radar measures from.
    private static BlockPos center(ShipState ship) {
        return BlockPos.containing(ShipShape.Frame.of(ship.position(), ship.yaw(), ship.pitch()).toWorld(0, ship.tier().exteriorHeight() / 2.0, 0));
    }

    private ModNetwork.RadarScan scan(ServerLevel level, ShipState ship, BlockPos center, int tier, RadarOre target) {
        // Finds were measured from where the sweep started; the ship has moved on since.
        int shiftX = findsCenter.getX() - center.getX(), shiftY = findsCenter.getY() - center.getY(), shiftZ = findsCenter.getZ() - center.getZ();
        int[] packed = findsTarget != target ? new int[0] : finds.stream().limit(MAX_FINDS)
                .mapToInt(f -> ModNetwork.RadarScan.find(f[0] + shiftX, f[1] + shiftY, f[2] + shiftZ, f[3])).toArray();
        int[] contacts = new int[0];
        int depth = -1, shore = -1;
        if (tier >= MAX_TIER) {
            List<Entity> nearby = level.getEntities((Entity) null, new AABB(center).inflate(CONTACT_RANGE, CONTACT_HEIGHT, CONTACT_RANGE),
                    entity -> isContact(entity) && !isOwn(ship, entity));
            nearby.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(Vec3.atCenterOf(center))));
            contacts = nearby.stream().limit(MAX_CONTACTS).mapToInt(entity -> ModNetwork.RadarScan.contact(
                    entity.getBlockX() - center.getX(), entity.getBlockZ() - center.getZ(), type(entity))).toArray();
            depth = Math.max(-1, column(level, center.getX(), center.getZ()));
            if (depth >= 0) {
                shore = shoreAhead(level, center, ship.yaw());
            }
        }
        return new ModNetwork.RadarScan(tier, oreRange(tier), target.ordinal(), packed, contacts, depth, shore);
    }

    private static boolean isContact(Entity entity) {
        return (entity instanceof LivingEntity && !(entity instanceof Player player && player.isSpectator())) || entity instanceof Boat
                || entity instanceof ShipSectionEntity && !(entity instanceof ShipPartEntity);
    }

    // The ship's own sections, and whoever rides in them.
    private static boolean isOwn(ShipState ship, Entity entity) {
        Entity section = entity.getRootVehicle();
        if (section.getUUID().equals(ship.id())) {
            return true;
        }
        for (int wagon = 1; wagon <= ship.wagonCount(); wagon++) {
            if (section.getUUID().equals(ship.wagonId(wagon))) {
                return true;
            }
        }
        return false;
    }

    private static int type(Entity entity) {
        if (entity instanceof Player) {
            return PLAYER;
        }
        if (entity instanceof Boat || entity instanceof ShipSectionEntity) {
            return VESSEL;
        }
        return entity.getType().getCategory().isFriendly() ? CREATURE : HOSTILE;
    }

    // Blocks along the heading to the first land, or -1 if there is none within range.
    private static int shoreAhead(ServerLevel level, BlockPos center, float yaw) {
        double hx = -Math.sin(Math.toRadians(yaw)), hz = Math.cos(Math.toRadians(yaw));
        for (int distance = 2; distance <= SHORE_RANGE; distance += 2) {
            if (column(level, (int) Math.floor(center.getX() + 0.5 + hx * distance), (int) Math.floor(center.getZ() + 0.5 + hz * distance)) == LAND) {
                return distance;
            }
        }
        return -1;
    }

    // UNKNOWN (not loaded), LAND, LAVA, or the depth of the water from the surface to the floor.
    private static int column(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return UNKNOWN;
        }
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        FluidState fluid = level.getFluidState(new BlockPos(x, surface - 1, z));
        if (fluid.is(FluidTags.WATER)) {
            return surface - level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        }
        return fluid.is(FluidTags.LAVA) ? LAVA : LAND;
    }

    // Ores: blocks of the target kind within range of the drillhead's middle, grouped 2x2x2, nearest first.

    private void sweep(ServerLevel level, BlockPos center, int range, RadarOre target) {
        boolean done = cursor >= sections.size();
        if (done || range != sweepRange || target != sweepTarget) {
            if (done && !sections.isEmpty() && range == sweepRange && target == sweepTarget) {
                List<int[]> found = new ArrayList<>(groups.values());
                found.sort(Comparator.comparingInt(f -> f[0] * f[0] + f[1] * f[1] + f[2] * f[2]));
                finds = found;
                findsCenter = sweepCenter;
                findsTarget = sweepTarget;
            }
            startSweep(center, range, target);
        }
        int up = sweepRange / 2;
        for (int read = 0; read < SECTIONS_PER_TICK && cursor < sections.size(); cursor++) {
            int[] at = sections.get(cursor);
            LevelChunk chunk = level.getChunkSource().getChunkNow(at[0], at[2]);
            if (chunk == null) {
                continue;
            }
            int index = chunk.getSectionIndexFromSectionY(at[1]);
            if (index < 0 || index >= chunk.getSectionsCount()) {
                continue;
            }
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir() || !section.maybeHas(state -> kinds.get(state.getBlock()) == sweepTarget)) {
                continue;
            }
            scanSection(section, at[0], at[1], at[2], up);
            read++;
        }
    }

    private void startSweep(BlockPos center, int range, RadarOre target) {
        sections.clear();
        groups.clear();
        cursor = 0;
        sweepCenter = center;
        sweepRange = range;
        sweepTarget = target;
        // The ore kinds come from the tags; re-read now and then, not every sweep.
        if (kinds.isEmpty() || ++sweeps % 20 == 0) {
            kinds = RadarOre.blocks();
        }
        int up = range / 2;
        for (int cx = (center.getX() - range) >> 4; cx <= (center.getX() + range) >> 4; cx++) {
            for (int cz = (center.getZ() - range) >> 4; cz <= (center.getZ() + range) >> 4; cz++) {
                for (int sy = (center.getY() - up) >> 4; sy <= (center.getY() + up) >> 4; sy++) {
                    sections.add(new int[] {cx, sy, cz});
                }
            }
        }
    }

    private void scanSection(LevelChunkSection section, int cx, int sy, int cz, int up) {
        for (int ly = 0; ly < 16; ly++) {
            int dy = (sy << 4) + ly - sweepCenter.getY();
            if (Math.abs(dy) > up) {
                continue;
            }
            for (int lz = 0; lz < 16; lz++) {
                int dz = (cz << 4) + lz - sweepCenter.getZ();
                for (int lx = 0; lx < 16; lx++) {
                    int dx = (cx << 4) + lx - sweepCenter.getX();
                    if (Math.abs(dx) > sweepRange || Math.abs(dz) > sweepRange || dx * dx + dz * dz > sweepRange * sweepRange) {
                        continue;
                    }
                    if (kinds.get(section.getBlockState(lx, ly, lz).getBlock()) == sweepTarget) {
                        groups.computeIfAbsent(BlockPos.asLong(dx >> 1, dy >> 1, dz >> 1), key -> new int[] {dx, dy, dz, 0})[3]++;
                    }
                }
            }
        }
    }
}
