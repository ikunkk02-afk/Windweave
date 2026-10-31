package com.shouyun.windweave.device;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class WindDeviceBlock extends BaseEntityBlock {
    private final int kind;
    public WindDeviceBlock(Properties props, int kind) {
        super(props); this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return simpleCodec(props -> new WindDeviceBlock(props, kind)); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind == 2 ? new WindControllerBlockEntity(pos, state) : new WindStationBlockEntity(pos, state);
    }
    @Override protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind == 2 ? box(1, 0, 1, 15, 13, 15) : Shapes.or(box(3, 0, 3, 13, 3, 13), box(7, 3, 7, 9, 15, 9));
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && kind != 2 ? createTickerHelper(type, WindDevices.STATION.get(), WindStationBlockEntity::tick) : null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof WindControllerBlockEntity control && player instanceof ServerPlayer server) {
                server.openMenu(control, buffer -> buffer.writeBlockPos(pos));
            } else player.displayClientMessage(WindReadout.message(com.shouyun.windweave.wind.WindField.sample(level,
                    net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0, .7, 0))), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
