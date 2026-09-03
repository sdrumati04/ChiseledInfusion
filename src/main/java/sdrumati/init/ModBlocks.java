package sdrumati.init;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import sdrumati.ChiseledInfusion;
import sdrumati.block.ChiseledInfuserBlock;

public class ModBlocks {
	public static final ResourceKey<Block> CHISELED_INFUSER_KEY = ResourceKey.create(
			Registries.BLOCK,
			ChiseledInfusion.id("chiseled_infuser")
	);

	public static final Block CHISELED_INFUSER = Registry.register(
			BuiltInRegistries.BLOCK,
			CHISELED_INFUSER_KEY,
			new ChiseledInfuserBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ENCHANTING_TABLE).setId(CHISELED_INFUSER_KEY))
	);

	public static final ResourceKey<Item> CHISELED_INFUSER_ITEM_KEY = ResourceKey.create(
			Registries.ITEM,
			ChiseledInfusion.id("chiseled_infuser")
	);

	public static final Item CHISELED_INFUSER_ITEM = Registry.register(
			BuiltInRegistries.ITEM,
			CHISELED_INFUSER_ITEM_KEY,
			new BlockItem(CHISELED_INFUSER, new Item.Properties().setId(CHISELED_INFUSER_ITEM_KEY).useBlockDescriptionPrefix())
	);

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(CHISELED_INFUSER_ITEM);
		});
	}
}
