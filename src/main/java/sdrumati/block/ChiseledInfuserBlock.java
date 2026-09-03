package sdrumati.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class ChiseledInfuserBlock extends Block {
	protected static final VoxelShape SHAPE = Block.column(16.0, 0.0, 12.0);
	public static final List<BlockPos> BOOKSHELF_OFFSETS = BlockPos.betweenClosedStream(-2, 0, -2, 2, 1, 2)
			.filter(pos -> Math.abs(pos.getX()) == 2 || Math.abs(pos.getZ()) == 2)
			.map(BlockPos::immutable)
			.toList();

	public ChiseledInfuserBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		super.animateTick(state, level, pos, random);
		for (BlockPos offset : BOOKSHELF_OFFSETS) {
			if (random.nextInt(16) == 0 && level.getBlockEntity(pos.offset(offset)) instanceof ChiseledBookShelfBlockEntity) {
				level.addParticle(
						ParticleTypes.ENCHANT,
						pos.getX() + 0.5,
						pos.getY() + 2.0,
						pos.getZ() + 0.5,
						(offset.getX() + random.nextFloat()) - 0.5,
						(offset.getY() - random.nextFloat() - 1.0),
						(offset.getZ() + random.nextFloat()) - 0.5
				);
			}
		}
	}
}
