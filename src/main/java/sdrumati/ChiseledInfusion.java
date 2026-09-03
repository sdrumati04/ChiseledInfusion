package sdrumati;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdrumati.config.ModConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class ChiseledInfusion implements ModInitializer {
	public static final String MOD_ID = "chiseledinfusion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String ITEM_TAG = "reenchant_item";

	// Track enchanting table positions that currently host a floating item
	private static final Set<BlockPos> ACTIVE_TABLES = Collections.synchronizedSet(new HashSet<>());

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Chiseled Infusion (Re_Enchanting v2 ported to 26.2)!");
		ModConfig.load();

		// Intercept right-clicks on the Enchanting Table
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}

			BlockPos pos = hitResult.getBlockPos();
			if (!world.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) {
				return InteractionResult.PASS;
			}

			AABB itemBox = new AABB(pos.getX(), pos.getY() + 0.5, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.8, pos.getZ() + 1.0);
			List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class, itemBox, e -> !e.isRemoved() && e.entityTags().contains(ITEM_TAG));
			boolean hasTableItem = !items.isEmpty();

			// If sneaking and table is empty with a placeable item, pass to allow placing blocks on the table
			if (!hasTableItem && player.isShiftKeyDown() && !player.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}

			// If client, return SUCCESS to prevent opening vanilla GUI and notify server
			if (world.isClientSide()) {
				return InteractionResult.SUCCESS;
			}

			return handleTableInteraction(player, world, pos, items);
		});

		// Ensure item drops immediately if the table is broken by a player
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (state.is(Blocks.ENCHANTING_TABLE) && !world.isClientSide()) {
				releaseTableItem(world, pos);
				ACTIVE_TABLES.remove(pos.immutable());
			}
			return true;
		});

		// Track floating items when chunks/entities load
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity item && item.entityTags().contains(ITEM_TAG)) {
				BlockPos tablePos = item.blockPosition();
				if (!world.getBlockState(tablePos).is(Blocks.ENCHANTING_TABLE)) {
					tablePos = tablePos.below();
				}
				if (world.getBlockState(tablePos).is(Blocks.ENCHANTING_TABLE)) {
					ACTIVE_TABLES.add(tablePos.immutable());
					secureTableItem(item, tablePos);
				} else {
					// Orphan item: release immediately
					releaseSingleItem(item);
				}
			}
		});

		// Periodic/Tick validation: check table existence and enforce item position & hopper/physics safety
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (ACTIVE_TABLES.isEmpty()) return;

			synchronized (ACTIVE_TABLES) {
				Iterator<BlockPos> it = ACTIVE_TABLES.iterator();
				while (it.hasNext()) {
					BlockPos pos = it.next();
					if (level.isLoaded(pos)) {
						if (!level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) {
							releaseTableItem(level, pos);
							it.remove();
						} else {
							// Safety check on floating items: keep locked at exact coordinate and max pickup delay
							AABB itemBox = new AABB(pos.getX(), pos.getY() + 0.5, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.8, pos.getZ() + 1.0);
							List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, itemBox, e -> !e.isRemoved() && e.entityTags().contains(ITEM_TAG));
							if (items.isEmpty()) {
								it.remove();
							} else {
								for (ItemEntity item : items) {
									secureTableItem(item, pos);
								}
							}
						}
					}
				}
			}
		});
	}

	private static void secureTableItem(ItemEntity item, BlockPos pos) {
		item.setPickUpDelay(32767);
		item.setNoGravity(true);
		item.setInvulnerable(true);
		item.noPhysics = true;
		item.setUnlimitedLifetime();
		item.setDeltaMovement(0, 0, 0);
		double targetX = pos.getX() + 0.5;
		double targetY = pos.getY() + 0.95;
		double targetZ = pos.getZ() + 0.5;
		if (item.distanceToSqr(targetX, targetY, targetZ) > 0.05) {
			item.setPos(targetX, targetY, targetZ);
		}
	}

	private static void releaseSingleItem(ItemEntity item) {
		item.setPickUpDelay(0);
		item.setNoGravity(false);
		item.setInvulnerable(false);
		item.noPhysics = false;
		item.removeTag(ITEM_TAG);
	}

	public static void releaseTableItem(Level world, BlockPos pos) {
		AABB itemBox = new AABB(pos.getX(), pos.getY() + 0.5, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.8, pos.getZ() + 1.0);
		List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class, itemBox, e -> e.entityTags().contains(ITEM_TAG));
		for (ItemEntity item : items) {
			releaseSingleItem(item);
		}
	}

	private static void sendActionBar(Player player, Component message) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendSystemMessage(message, true);
		} else {
			player.sendSystemMessage(message);
		}
	}

	private static boolean isLineOfSightClear(Level world, BlockPos tablePos, int dx, int dy, int dz) {
		if (dx == 0 && dz == 0) return false;
		if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
			return true;
		}
		BlockPos midPos = tablePos.offset(dx / 2, dy, dz / 2);
		return world.getBlockState(midPos).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER);
	}

	private InteractionResult handleTableInteraction(Player player, Level world, BlockPos pos, List<ItemEntity> items) {
		ItemEntity currentItemEntity = items.isEmpty() ? null : items.get(0);

		ItemStack held = player.getMainHandItem();
		boolean isCreative = player.getAbilities().instabuild;
		ModConfig config = ModConfig.INSTANCE;

		// Case 1: No item on the enchanting table
		if (currentItemEntity == null) {
			if (held.isEmpty()) {
				return InteractionResult.SUCCESS;
			}

			// Place 1 item from hand onto the table
			ItemStack placedItem = isCreative ? held.copyWithCount(1) : held.split(1);

			ItemEntity itemEntity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, placedItem);
			secureTableItem(itemEntity, pos);
			itemEntity.addTag(ITEM_TAG);
			world.addFreshEntity(itemEntity);

			ACTIVE_TABLES.add(pos.immutable());
			world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
			return InteractionResult.SUCCESS;
		}

		// Prevent race conditions: ensure entity is alive and valid
		if (currentItemEntity.isRemoved()) {
			return InteractionResult.PASS;
		}

		ItemStack tableItem = currentItemEntity.getItem();

		// Feature: Inspection via Sneak + Right-Click on occupied table (shows current enchantments)
		if (player.isShiftKeyDown()) {
			displayItemEnchantments(player, tableItem);
			return InteractionResult.SUCCESS;
		}

		Item catalystItem = config.getCatalystItem();

		// Subcase 2A: Player interacts holding Catalyst (default Lapis Lazuli) -> Perform Enchantment Infusion
		if (held.is(catalystItem)) {
			int requiredXP = config.xpLevelsCost;
			int requiredCatalyst = config.lapisCost;

			if (player.experienceLevel < requiredXP && !isCreative) {
				sendActionBar(player, Component.literal("Non hai abbastanza esperienza! Ti servono almeno " + requiredXP + " livelli.").withStyle(ChatFormatting.RED));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			if (held.getCount() < requiredCatalyst && !isCreative) {
				sendActionBar(player, Component.literal("Non hai abbastanza catalizzatore! Ti servono " + requiredCatalyst + "x " + catalystItem.getName(held).getString()).withStyle(ChatFormatting.RED));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			boolean isRegularBook = tableItem.is(Items.BOOK);
			boolean isEnchantedBook = tableItem.is(Items.ENCHANTED_BOOK);

			ItemEnchantments currentEnchants = isEnchantedBook
					? tableItem.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
					: tableItem.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
			ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(currentEnchants);

			int booksFound = 0;
			int upgradesApplied = 0;
			List<BlockPos> contributingBookshelves = new ArrayList<>();

			int rHoriz = config.scanRadiusHorizontal;
			int rVert = config.scanRadiusVertical;

			// Scan the configured area around the table for Chiseled Bookshelves with clear line of sight
			for (int dx = -rHoriz; dx <= rHoriz; dx++) {
				for (int dz = -rHoriz; dz <= rHoriz; dz++) {
					for (int dy = 0; dy <= rVert; dy++) {
						if (!isLineOfSightClear(world, pos, dx, dy, dz)) {
							continue;
						}

						BlockPos checkPos = pos.offset(dx, dy, dz);
						if (world.getBlockEntity(checkPos) instanceof ChiseledBookShelfBlockEntity bookshelf) {
							boolean shelfContributed = false;
							for (int slot = 0; slot < bookshelf.getContainerSize(); slot++) {
								ItemStack book = bookshelf.getItem(slot);
								if (!book.isEmpty()) {
									ItemEnchantments stored = book.get(DataComponents.STORED_ENCHANTMENTS);
									if (stored != null && !stored.isEmpty()) {
										booksFound++;
										for (Holder<Enchantment> ench : stored.keySet()) {
											int currentLvl = mutable.getLevel(ench);
											int bookLvl = stored.getLevel(ench);
											if (bookLvl > currentLvl) {
												upgradesApplied++;
												shelfContributed = true;
												mutable.set(ench, bookLvl);
											}
										}
									}
								}
							}
							if (shelfContributed) {
								contributingBookshelves.add(checkPos);
							}
						}
					}
				}
			}

			if (booksFound == 0) {
				sendActionBar(player, Component.literal("Nessun libro incantato trovato nelle librerie scolpite vicine!").withStyle(ChatFormatting.RED));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			// Protection against XP/Item waste: if no enchantment can be upgraded, abort cleanly
			if (upgradesApplied == 0) {
				sendActionBar(player, Component.literal("L'oggetto possiede già il massimo grado per tutti gli incantesimi disponibili!").withStyle(ChatFormatting.YELLOW));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			// Deduct costs
			if (!isCreative) {
				held.shrink(requiredCatalyst);
				player.giveExperienceLevels(-requiredXP);
			}

			// Apply enchantments (Sandbox mode: no limits)
			if (isRegularBook) {
				ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
				enchantedBook.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
				if (config.clearRepairCost) {
					enchantedBook.remove(DataComponents.REPAIR_COST);
				}
				tableItem = enchantedBook;
			} else if (isEnchantedBook) {
				tableItem.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
				if (config.clearRepairCost) {
					tableItem.remove(DataComponents.REPAIR_COST);
				}
			} else {
				tableItem.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
				if (config.clearRepairCost) {
					tableItem.remove(DataComponents.REPAIR_COST);
				}
			}

			currentItemEntity.setItem(tableItem);

			// Visual effects: Runic glyphs flying from contributing bookshelves towards the table item
			if (world instanceof ServerLevel serverLevel) {
				RandomSource random = world.getRandom();
				for (BlockPos shelfPos : contributingBookshelves) {
					int dx = shelfPos.getX() - pos.getX();
					int dy = shelfPos.getY() - pos.getY();
					int dz = shelfPos.getZ() - pos.getZ();

					for (int p = 0; p < 8; p++) {
						double pOffsetX = (double) dx + (random.nextFloat() - 0.5) * 0.4;
						double pOffsetY = (double) dy - (random.nextFloat() * 0.4) - 0.4;
						double pOffsetZ = (double) dz + (random.nextFloat() - 0.5) * 0.4;

						serverLevel.sendParticles(
								ParticleTypes.ENCHANT,
								pos.getX() + 0.5,
								pos.getY() + 1.25,
								pos.getZ() + 0.5,
								0,
								pOffsetX,
								pOffsetY,
								pOffsetZ,
								1.0
						);
					}
					// Varied pitch bookshelf sound
					float shelfPitch = 1.1F + (random.nextFloat() - 0.5F) * 0.2F;
					world.playSound(null, shelfPos, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 0.7F, shelfPitch);
				}

				// Swirling infusion burst at table
				serverLevel.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 60, 0.5, 0.5, 0.5, 0.1);
			}

			float usePitch = 0.95F + (world.getRandom().nextFloat() * 0.1F);
			world.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, usePitch);
			sendActionBar(player, Component.literal("Oggetto incantato con successo! (+" + upgradesApplied + " incantesimi/livelli)").withStyle(ChatFormatting.GREEN));
			return InteractionResult.SUCCESS;
		}

		// Subcase 2B: Player interacts with empty hand or any other item -> Retrieve item
		ItemStack toReturn = tableItem.copy();
		currentItemEntity.discard();
		ACTIVE_TABLES.remove(pos.immutable());
		if (!player.getInventory().add(toReturn)) {
			player.drop(toReturn, false);
		}
		float pickupPitch = 1.0F + (world.getRandom().nextFloat() - 0.5F) * 0.2F;
		world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, pickupPitch);
		return InteractionResult.SUCCESS;
	}

	private static void displayItemEnchantments(Player player, ItemStack stack) {
		boolean isEnchantedBook = stack.is(Items.ENCHANTED_BOOK);
		ItemEnchantments enchants = isEnchantedBook
				? stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
				: stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

		if (enchants.isEmpty()) {
			sendActionBar(player, Component.literal(stack.getHoverName().getString() + " non ha alcun incantesimo.").withStyle(ChatFormatting.GRAY));
			return;
		}

		MutableComponent summary = Component.literal("[" + stack.getHoverName().getString() + "]: ").withStyle(ChatFormatting.GOLD);
		boolean first = true;
		for (Holder<Enchantment> ench : enchants.keySet()) {
			if (!first) {
				summary.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
			}
			summary.append(Enchantment.getFullname(ench, enchants.getLevel(ench)));
			first = false;
		}
		sendActionBar(player, summary);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
