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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sdrumati.block.ChiseledInfuserBlock;
import sdrumati.config.ModConfig;
import sdrumati.init.ModBlocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChiseledInfusion implements ModInitializer {
	public static final String MOD_ID = "chiseledinfusion";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final String ITEM_TAG = "reenchant_item";
	public static final double ITEM_Y_OFFSET = 0.80; // Raised by ~2.6 pixels from 0.6375 (12.8 pixels from floor, ~1 pixel above table surface)

	// Track table positions that currently host a floating item
	private static final Set<BlockPos> ACTIVE_TABLES = Collections.synchronizedSet(new HashSet<>());

	// Suppress stare preview temporarily after a click so the player can read click feedback (success/failure)
	private static final Map<UUID, Long> PREVIEW_SUPPRESS_UNTIL = new ConcurrentHashMap<>();

	// Micro-cache for scan results to optimize performance when players stare at the table
	private record CachedScan(long tick, ItemStack tableItem, InfusionScanResult result) {}
	private static final Map<BlockPos, CachedScan> SCAN_CACHE = new ConcurrentHashMap<>();

	public static AABB getTableItemBox(BlockPos pos) {
		return new AABB(pos.getX(), pos.getY() + 0.2, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.5, pos.getZ() + 1.0);
	}

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Chiseled Infusion (Re_Enchanting v2 ported to 26.2)!");
		ModBlocks.register();
		ModConfig.load();

		// Intercept right-clicks on the Chiseled Infuser
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}

			BlockPos pos = hitResult.getBlockPos();
			if (!world.getBlockState(pos).is(ModBlocks.CHISELED_INFUSER)) {
				return InteractionResult.PASS;
			}

			// If sneaking and holding an item, pass to allow placing blocks on or around the table
			if (player.isShiftKeyDown() && !player.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}

			List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class, getTableItemBox(pos), e -> !e.isRemoved() && e.entityTags().contains(ITEM_TAG));

			// If client, return SUCCESS to prevent opening GUI and notify server
			if (world.isClientSide()) {
				return InteractionResult.SUCCESS;
			}

			return handleTableInteraction(player, world, pos, items);
		});

		// Ensure item drops immediately if the table is broken by a player
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (state.is(ModBlocks.CHISELED_INFUSER) && !world.isClientSide()) {
				SCAN_CACHE.remove(pos.immutable());
				releaseTableItem(world, pos);
				ACTIVE_TABLES.remove(pos.immutable());
			}
			return true;
		});

		// Track floating items when chunks/entities load
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof ItemEntity item && item.entityTags().contains(ITEM_TAG)) {
				BlockPos tablePos = item.blockPosition();
				if (!world.getBlockState(tablePos).is(ModBlocks.CHISELED_INFUSER)) {
					tablePos = tablePos.below();
				}
				if (world.getBlockState(tablePos).is(ModBlocks.CHISELED_INFUSER)) {
					ACTIVE_TABLES.add(tablePos.immutable());
					secureTableItem(item, tablePos);
				} else {
					// Orphan item: release immediately
					releaseSingleItem(item);
				}
			}
		});

		// Periodic/Tick validation: check table existence, safety, and player staring preview
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (level instanceof ServerLevel serverLevel) {
				// Player staring check: every 4 ticks (~5 times/sec)
				for (ServerPlayer player : serverLevel.players()) {
					if (player.tickCount % 4 == 0) {
						checkPlayerLookingAtTable(player, serverLevel);
					}
				}
			}

			if (ACTIVE_TABLES.isEmpty()) return;

			synchronized (ACTIVE_TABLES) {
				Iterator<BlockPos> it = ACTIVE_TABLES.iterator();
				while (it.hasNext()) {
					BlockPos pos = it.next();
					if (level.isLoaded(pos)) {
						if (!level.getBlockState(pos).is(ModBlocks.CHISELED_INFUSER)) {
							SCAN_CACHE.remove(pos);
							releaseTableItem(level, pos);
							it.remove();
						} else {
							// Safety check on floating items: keep locked at exact coordinate and max pickup delay
							List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, getTableItemBox(pos), e -> !e.isRemoved() && e.entityTags().contains(ITEM_TAG));
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

	private static void checkPlayerLookingAtTable(ServerPlayer player, ServerLevel level) {
		// If player recently clicked, do not overwrite their action bar message yet
		if (level.getGameTime() < PREVIEW_SUPPRESS_UNTIL.getOrDefault(player.getUUID(), 0L)) {
			return;
		}

		HitResult hit = player.pick(5.0D, 0.0F, false);
		if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
			return;
		}

		BlockPos pos = blockHit.getBlockPos();
		if (!level.getBlockState(pos).is(ModBlocks.CHISELED_INFUSER)) {
			return;
		}

		List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, getTableItemBox(pos), e -> !e.isRemoved() && e.entityTags().contains(ITEM_TAG));
		if (items.isEmpty()) {
			return;
		}

		ItemStack tableItem = items.get(0).getItem();
		ModConfig config = ModConfig.INSTANCE;

		// Show cost and enchantment preview by default whenever looking at the table with an item on it
		displayInfusionPreview(player, level, pos, tableItem, config);
	}

	public static int countNormalBookshelves(Level world, BlockPos pos) {
		int count = 0;
		for (BlockPos offset : ChiseledInfuserBlock.BOOKSHELF_OFFSETS) {
			BlockPos checkPos = pos.offset(offset);
			if (ChiseledInfuserBlock.isAnyBookshelf(world, checkPos)) {
				// Corner bookshelves are counted directly so they are never blocked by line-of-sight
				if (Math.abs(offset.getX()) == 2 && Math.abs(offset.getZ()) == 2) {
					count++;
					continue;
				}

				BlockPos midPos = pos.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2);
				BlockState midState = world.getBlockState(midPos);
				// Unobstructed if midState is non-opaque/passable, a transmitter, or another bookshelf
				if (!midState.isSolidRender()
						|| midState.is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)
						|| ChiseledInfuserBlock.isAnyBookshelf(world, midPos)) {
					count++;
				}
			}
		}
		return Math.min(15, count);
	}

	private static record InfusionScanResult(
			int booksFound,
			int applicableBooksFound,
			int upgradesApplied,
			int totalXpCost,
			int totalCatalystCost,
			int bookshelfCount,
			boolean hasCryingObsidian,
			boolean needsCryingObsidian,
			List<Component> appliedEnchantments,
			ItemEnchantments.Mutable mutableEnchants,
			List<BlockPos> contributingBookshelves
	) {}

	private static InfusionScanResult scanInfusion(Level world, BlockPos pos, ItemStack tableItem, ModConfig config) {
		CachedScan cached = SCAN_CACHE.get(pos);
		if (cached != null && world.getGameTime() - cached.tick() < 5L && ItemStack.matches(cached.tableItem(), tableItem)) {
			return cached.result();
		}

		boolean isRegularBook = tableItem.is(Items.BOOK);
		boolean isEnchantedBook = tableItem.is(Items.ENCHANTED_BOOK);
		boolean isAnyBook = isRegularBook || isEnchantedBook;

		ItemEnchantments currentEnchants = isEnchantedBook
				? tableItem.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY)
				: tableItem.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(currentEnchants);

		int bookshelfCount = countNormalBookshelves(world, pos);
		boolean hasCryingObsidian = world.getBlockState(pos.below()).is(Blocks.CRYING_OBSIDIAN);

		int booksFound = 0;
		int applicableBooksFound = 0;
		List<BlockPos> contributingBookshelves = new ArrayList<>();

		int rHoriz = config.scanRadiusHorizontal;
		int rVert = config.scanRadiusVertical;

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
										// Check item compatibility: books accept anything, other items must support the enchantment
										if (!isAnyBook && !ench.value().canEnchant(tableItem)) {
											continue;
										}

										applicableBooksFound++;
										int currentLvl = mutable.getLevel(ench);
										int bookLvl = stored.getLevel(ench);

										if (bookLvl > currentLvl) {
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

		int upgradesApplied = 0;
		int totalXpCost = 0;
		int totalCatalystCost = 0;
		List<Component> appliedEnchantments = new ArrayList<>();

		for (Holder<Enchantment> ench : mutable.keySet()) {
			int beforeLvl = currentEnchants.getLevel(ench);
			int targetLvl = mutable.getLevel(ench);
			if (targetLvl > beforeLvl) {
				upgradesApplied++;
				// Cost is based on delta of upgrade (e.g. going from IV to V costs 1 level * multiplier)
				totalXpCost += (targetLvl - beforeLvl) * config.xpMultiplierPerLevel;
				totalCatalystCost += config.lapisCostPerUpgrade;
				appliedEnchantments.add(Enchantment.getFullname(ench, targetLvl));
			}
		}

		if (config.maxXpCost > 0 && totalXpCost > config.maxXpCost) {
			totalXpCost = config.maxXpCost;
		}

		// Detect if Crying Obsidian is needed (incompatible enchantments or levels exceeding vanilla cap)
		boolean hasIncompatible = false;
		boolean hasOverCap = false;

		List<Holder<Enchantment>> enchantsList = new ArrayList<>(mutable.keySet());
		for (int i = 0; i < enchantsList.size(); i++) {
			Holder<Enchantment> e1 = enchantsList.get(i);
			int lvl = mutable.getLevel(e1);
			if (lvl > e1.value().getMaxLevel()) {
				hasOverCap = true;
			}
			for (int j = i + 1; j < enchantsList.size(); j++) {
				Holder<Enchantment> e2 = enchantsList.get(j);
				if (!Enchantment.areCompatible(e1, e2)) {
					hasIncompatible = true;
				}
			}
		}

		boolean needsCryingObsidian = !hasCryingObsidian && (hasIncompatible || hasOverCap);

		InfusionScanResult result = new InfusionScanResult(booksFound, applicableBooksFound, upgradesApplied, totalXpCost, totalCatalystCost, bookshelfCount, hasCryingObsidian, needsCryingObsidian, appliedEnchantments, mutable, contributingBookshelves);
		SCAN_CACHE.put(pos.immutable(), new CachedScan(world.getGameTime(), tableItem.copy(), result));
		return result;
	}

	private static int countCatalystInInventory(Player player, Item catalystItem) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(catalystItem)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void displayInfusionPreview(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack tableItem, ModConfig config) {
		InfusionScanResult scan = scanInfusion(level, pos, tableItem, config);
		if (scan.booksFound() == 0) {
			sendActionBar(player, Component.translatable("message.chiseledinfusion.no_books_found").withStyle(ChatFormatting.GRAY));
			return;
		}

		if (scan.applicableBooksFound() == 0) {
			sendActionBar(player, Component.translatable("message.chiseledinfusion.not_applicable").withStyle(ChatFormatting.GRAY));
			return;
		}

		if (scan.upgradesApplied() == 0) {
			sendActionBar(player, Component.translatable("message.chiseledinfusion.max_enchantments").withStyle(ChatFormatting.GRAY));
			return;
		}

		Item catalystItem = config.getCatalystItem();
		int catalystCount = countCatalystInInventory(player, catalystItem);

		boolean isCreative = player.getAbilities().instabuild;
		boolean hasXp = isCreative || player.experienceLevel >= scan.totalXpCost();
		boolean hasCatalyst = isCreative || catalystCount >= scan.totalCatalystCost();
		int maxLevelAllowed = scan.bookshelfCount() >= 15 ? Integer.MAX_VALUE : scan.bookshelfCount() * 2;
		boolean hasEnoughBookshelves = isCreative || scan.totalXpCost() <= maxLevelAllowed;

		// Preview turns RED if unaffordable OR if Crying Obsidian is required to unlock incompatible/over-cap enchants
		boolean canAfford = hasXp && hasCatalyst && hasEnoughBookshelves && !scan.needsCryingObsidian();
		ChatFormatting color = canAfford ? ChatFormatting.GREEN : ChatFormatting.RED;

		Component catalystName = catalystItem.getName(new ItemStack(catalystItem));
		Component enchantsText = scan.appliedEnchantments().stream()
				.reduce((c1, c2) -> Component.empty().append(c1).append(", ").append(c2))
				.orElse(Component.empty());

		sendActionBar(player, Component.translatable("message.chiseledinfusion.cost_preview_with_enchants",
				enchantsText, scan.totalXpCost(), scan.totalCatalystCost(), catalystName).withStyle(color));
	}

	private static void secureTableItem(ItemEntity item, BlockPos pos) {
		item.setPickUpDelay(32767);
		item.setNoGravity(true);
		item.setInvulnerable(true);
		item.noPhysics = true;
		item.setUnlimitedLifetime();
		item.setDeltaMovement(0, 0, 0);

		double targetX = pos.getX() + 0.5;
		double targetY = pos.getY() + ITEM_Y_OFFSET;
		double targetZ = pos.getZ() + 0.5;
		item.snapTo(targetX, targetY, targetZ, item.getYRot(), item.getXRot());
		item.setOldPosAndRot();
	}

	private static void releaseSingleItem(ItemEntity item) {
		item.setPickUpDelay(0);
		item.setNoGravity(false);
		item.setInvulnerable(false);
		item.noPhysics = false;
		item.removeTag(ITEM_TAG);
	}

	public static void releaseTableItem(Level world, BlockPos pos) {
		List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class, getTableItemBox(pos), e -> e.entityTags().contains(ITEM_TAG));
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

	private static void sendClickFeedback(Player player, Level world, Component message) {
		// Keep click message on screen for 45 ticks (~2.25s) before staring preview resumes
		PREVIEW_SUPPRESS_UNTIL.put(player.getUUID(), world.getGameTime() + 45L);
		sendActionBar(player, message);
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

		// Case 1: No item on the table
		if (currentItemEntity == null) {
			if (held.isEmpty()) {
				return InteractionResult.SUCCESS;
			}

			// Place 1 item from hand onto the table
			ItemStack placedItem = isCreative ? held.copyWithCount(1) : held.split(1);

			double spawnX = pos.getX() + 0.5;
			double spawnY = pos.getY() + ITEM_Y_OFFSET;
			double spawnZ = pos.getZ() + 0.5;
			ItemEntity itemEntity = new ItemEntity(world, spawnX, spawnY, spawnZ, placedItem, 0.0, 0.0, 0.0);
			secureTableItem(itemEntity, pos);
			itemEntity.addTag(ITEM_TAG);
			world.addFreshEntity(itemEntity);

			ACTIVE_TABLES.add(pos.immutable());
			SCAN_CACHE.remove(pos.immutable());
			world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
			return InteractionResult.SUCCESS;
		}

		// Prevent race conditions: ensure entity is alive and valid
		if (currentItemEntity.isRemoved()) {
			return InteractionResult.PASS;
		}

		ItemStack tableItem = currentItemEntity.getItem();
		Item catalystItem = config.getCatalystItem();

		// Subcase 2A: Player interacts holding Catalyst (default Lapis Lazuli) -> Perform Enchantment Infusion
		if (held.is(catalystItem)) {
			InfusionScanResult scan = scanInfusion(world, pos, tableItem, config);

			if (scan.booksFound() == 0) {
				sendClickFeedback(player, world, Component.translatable("message.chiseledinfusion.no_books_found").withStyle(ChatFormatting.RED));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			if (scan.applicableBooksFound() == 0) {
				sendClickFeedback(player, world, Component.translatable("message.chiseledinfusion.not_applicable").withStyle(ChatFormatting.RED));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			// Protection against XP/Item waste: if no enchantment can be upgraded, notify
			if (scan.upgradesApplied() == 0) {
				sendClickFeedback(player, world, Component.translatable("message.chiseledinfusion.max_enchantments").withStyle(ChatFormatting.RED));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			// Prioritize bookshelf power check FIRST: if bookshelves are insufficient, alert about bookshelves
			int maxLevelAllowed = scan.bookshelfCount() >= 15 ? Integer.MAX_VALUE : scan.bookshelfCount() * 2;
			boolean missingBookshelves = !isCreative && scan.totalXpCost() > maxLevelAllowed;

			if (missingBookshelves) {
				int needed = Math.min(15, (int) Math.ceil(scan.totalXpCost() / 2.0));
				sendClickFeedback(player, world, Component.translatable("message.chiseledinfusion.not_enough_bookshelves", scan.bookshelfCount(), needed).withStyle(ChatFormatting.RED));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			// Then check Crying Obsidian requirement: if incompatible or over-cap enchantments are present
			if (scan.needsCryingObsidian()) {
				sendClickFeedback(player, world, Component.translatable("message.chiseledinfusion.requires_crying_obsidian_unlock").withStyle(ChatFormatting.RED));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			int catalystCount = countCatalystInInventory(player, catalystItem);
			boolean missingXp = player.experienceLevel < scan.totalXpCost() && !isCreative;
			boolean missingCatalyst = catalystCount < scan.totalCatalystCost() && !isCreative;
			Component catalystName = catalystItem.getName(new ItemStack(catalystItem));

			if (missingXp && missingCatalyst) {
				sendClickFeedback(player, world, Component.translatable("message.chiseledinfusion.not_enough_both", scan.totalXpCost(), scan.totalCatalystCost(), catalystName).withStyle(ChatFormatting.RED));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			if (missingXp) {
				sendClickFeedback(player, world, Component.translatable("message.chiseledinfusion.not_enough_xp", scan.totalXpCost()).withStyle(ChatFormatting.RED));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			if (missingCatalyst) {
				sendClickFeedback(player, world, Component.translatable("message.chiseledinfusion.not_enough_catalyst", scan.totalCatalystCost(), catalystName).withStyle(ChatFormatting.RED));
				world.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}

			// Deduct costs
			if (!isCreative) {
				int toDeduct = scan.totalCatalystCost();
				if (held.is(catalystItem)) {
					int fromHand = Math.min(held.getCount(), toDeduct);
					held.shrink(fromHand);
					toDeduct -= fromHand;
				}
				if (toDeduct > 0) {
					for (int i = 0; i < player.getInventory().getContainerSize() && toDeduct > 0; i++) {
						ItemStack invStack = player.getInventory().getItem(i);
						if (invStack.is(catalystItem)) {
							int take = Math.min(invStack.getCount(), toDeduct);
							invStack.shrink(take);
							toDeduct -= take;
						}
					}
				}
				player.giveExperienceLevels(-scan.totalXpCost());
			}

			boolean isRegularBook = tableItem.is(Items.BOOK);
			boolean isEnchantedBook = tableItem.is(Items.ENCHANTED_BOOK);

			// Apply enchantments
			if (isRegularBook) {
				ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
				enchantedBook.set(DataComponents.STORED_ENCHANTMENTS, scan.mutableEnchants().toImmutable());
				if (config.clearRepairCost) {
					enchantedBook.remove(DataComponents.REPAIR_COST);
				}
				tableItem = enchantedBook;
			} else if (isEnchantedBook) {
				tableItem.set(DataComponents.STORED_ENCHANTMENTS, scan.mutableEnchants().toImmutable());
				if (config.clearRepairCost) {
					tableItem.remove(DataComponents.REPAIR_COST);
				}
			} else {
				tableItem.set(DataComponents.ENCHANTMENTS, scan.mutableEnchants().toImmutable());
				if (config.clearRepairCost) {
					tableItem.remove(DataComponents.REPAIR_COST);
				}
			}

			currentItemEntity.setItem(tableItem);
			SCAN_CACHE.remove(pos.immutable());

			// Visual effects: Runic glyphs flying from contributing bookshelves towards the table item
			if (world instanceof ServerLevel serverLevel) {
				RandomSource random = world.getRandom();
				for (BlockPos shelfPos : scan.contributingBookshelves()) {
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
								pos.getY() + ITEM_Y_OFFSET + 0.3,
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
				serverLevel.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + ITEM_Y_OFFSET + 0.25, pos.getZ() + 0.5, 60, 0.5, 0.5, 0.5, 0.1);
			}

			float usePitch = 0.95F + (world.getRandom().nextFloat() * 0.1F);
			world.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, usePitch);
			sendClickFeedback(player, world, Component.translatable("message.chiseledinfusion.success").withStyle(ChatFormatting.GREEN));
			return InteractionResult.SUCCESS;
		}

		// Subcase 2B: Player interacts with empty hand or any other item -> Retrieve item
		ItemStack toReturn = tableItem.copy();
		currentItemEntity.discard();
		ACTIVE_TABLES.remove(pos.immutable());
		SCAN_CACHE.remove(pos.immutable());
		if (!player.getInventory().add(toReturn)) {
			player.drop(toReturn, false);
		}
		float pickupPitch = 1.0F + (world.getRandom().nextFloat() - 0.5F) * 0.2F;
		world.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, pickupPitch);
		return InteractionResult.SUCCESS;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
