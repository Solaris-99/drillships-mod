package com.solaris99.drillship.block;

import com.mojang.serialization.MapCodec;
import com.solaris99.drillship.menu.StatusMenu;
import com.solaris99.drillship.ship.ShipManager;
import com.solaris99.drillship.ship.ShipState;
import com.solaris99.drillship.ship.ShipTravel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cockpit furniture, facing its operator: the driver's chair (sit down to drive) and the consoles around it, which
 * show the ship's status. Its model sits inside the block, so it is drawn with its neighbours' faces showing.
 */
public class CockpitBlock extends HorizontalDirectionalBlock {
    public enum Kind {
        CHAIR(Block.box(2, 0, 2, 14, 16, 14)),
        CONSOLE(Block.box(1, 0, 1, 15, 14, 15)),
        DESK(Block.box(0, 0, 0, 16, 16, 16));

        private final VoxelShape shape;

        Kind(VoxelShape shape) {
            this.shape = shape;
        }
    }

    private final Kind kind;
    private final MapCodec<CockpitBlock> codec;

    public CockpitBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new CockpitBlock(kind, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind.shape;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ShipState ship = ShipManager.get(serverPlayer.server).byInteriorPos(pos);
        if (ship == null) {
            return InteractionResult.FAIL;
        }
        if (kind == Kind.CHAIR) {
            ShipTravel.startDriving(serverPlayer, ship);
        } else {
            StatusMenu.open(serverPlayer, ship, pos);
        }
        return InteractionResult.CONSUME;
    }
}
