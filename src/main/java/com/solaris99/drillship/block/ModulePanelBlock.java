package com.solaris99.drillship.block;

import java.util.List;

import com.solaris99.drillship.menu.ModulePanelMenu;
import com.solaris99.drillship.ship.RadioStation;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipModule;
import com.solaris99.drillship.ship.ShipState;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A module panel in the drillhead's left wall: four bays, each taking one module. Using it opens the panel's slots.
 * The block shows what is plugged into each bay, and the radio's station on the panel holding the ship's first radio,
 * which is where the radio is heard inside.
 */
public class ModulePanelBlock extends Block {
    public enum Bay implements StringRepresentable {
        EMPTY, RADAR, RADIO;

        static Bay of(ShipModule module) {
            return module == null ? EMPTY : module == ShipModule.RADAR ? RADAR : RADIO;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final List<EnumProperty<Bay>> BAYS = List.of(EnumProperty.create("bay0", Bay.class), EnumProperty.create("bay1", Bay.class),
            EnumProperty.create("bay2", Bay.class), EnumProperty.create("bay3", Bay.class));
    public static final IntegerProperty STATION = IntegerProperty.create("station", 0, RadioStation.values().length - 1);

    public ModulePanelBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(STATION, 0);
        for (EnumProperty<Bay> bay : BAYS) {
            state = state.setValue(bay, Bay.EMPTY);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        BAYS.forEach(builder::add);
        builder.add(STATION);
    }

    /** How panel {@code panel} of the ship looks; {@code station} is the radio's, or 0 when it plays elsewhere. */
    public BlockState showing(ShipState ship, int panel, int station) {
        BlockState state = defaultBlockState().setValue(STATION, station);
        for (int bay = 0; bay < BAYS.size(); bay++) {
            state = state.setValue(BAYS.get(bay), Bay.of(ship.moduleAt(panel * ShipState.MODULES_PER_PANEL + bay)));
        }
        return state;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ShipState ship = ShipManager.get(serverPlayer.server).byInteriorPos(pos);
        int panel = ship == null ? -1 : ship.layout().modulePanels().indexOf(pos);
        if (panel < 0) {
            return InteractionResult.FAIL;
        }
        ModulePanelMenu.open(serverPlayer, ship, pos, panel);
        return InteractionResult.CONSUME;
    }
}
