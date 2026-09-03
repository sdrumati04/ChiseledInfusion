package sdrumati.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public class ChiseledInfuserBlock extends Block {
	protected static final VoxelShape SHAPE = Block.column(16.0, 0.0, 12.0);
	public static final List<BlockPos> BOOKSHELF_OFFSETS = BlockPos.betweenClosedStream(-2, -1, -2, 2, 2, 2)
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
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() instanceof ItemEntity item) {
			if (item.isNoGravity() || item.getY() < pos.getY() + 0.85) {
				return Shapes.empty();
			}
		}
		return SHAPE;
	}

	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	public static boolean isAnyBookshelf(Level level, BlockPos checkPos) {
		BlockState bs = level.getBlockState(checkPos);
		return bs.is(Blocks.BOOKSHELF)
				|| bs.is(Blocks.CHISELED_BOOKSHELF)
				|| bs.is(BlockTags.ENCHANTMENT_POWER_PROVIDER)
				|| level.getBlockEntity(checkPos) instanceof ChiseledBookShelfBlockEntity;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		super.animateTick(state, level, pos, random);

		// Portal particles swirling up if Crying Obsidian is placed underneath
		if (level.getBlockState(pos.below()).is(Blocks.CRYING_OBSIDIAN)) {
			if (random.nextInt(3) == 0) {
				level.addParticle(
						ParticleTypes.PORTAL,
						pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
						pos.getY() + 0.8,
						pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
						(random.nextDouble() - 0.5) * 0.2,
						(random.nextDouble() - 0.5) * 0.2,
						(random.nextDouble() - 0.5) * 0.2
				);
			}
		}

		// Enchant glyph particles from bookshelves
		for (BlockPos offset : BOOKSHELF_OFFSETS) {
			if (random.nextInt(16) == 0) {
				BlockPos checkPos = pos.offset(offset);
				if (isAnyBookshelf(level, checkPos)) {
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
}
