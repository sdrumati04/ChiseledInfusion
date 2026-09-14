package sdrumati.client.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import sdrumati.config.ModConfig;

public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			ConfigBuilder builder = ConfigBuilder.create()
					.setParentScreen(parent)
					.setTitle(Component.translatable("title.chiseledinfusion.config"));

			ConfigEntryBuilder entryBuilder = builder.entryBuilder();
			ConfigCategory general = builder.getOrCreateCategory(Component.translatable("category.chiseledinfusion.general"));

			general.addEntry(entryBuilder.startBooleanToggle(
							Component.translatable("option.chiseledinfusion.enable_vanilla_enchanting_table"),
							ModConfig.INSTANCE.enableVanillaEnchantingTable)
					.setDefaultValue(false)
					.setTooltip(Component.translatable("tooltip.chiseledinfusion.enable_vanilla_enchanting_table"))
					.setSaveConsumer(val -> ModConfig.INSTANCE.enableVanillaEnchantingTable = val)
					.build());

			general.addEntry(entryBuilder.startIntField(
							Component.translatable("option.chiseledinfusion.xp_multiplier"),
							ModConfig.INSTANCE.xpMultiplierPerLevel)
					.setDefaultValue(3)
					.setMin(1)
					.setTooltip(Component.translatable("tooltip.chiseledinfusion.xp_multiplier"))
					.setSaveConsumer(val -> ModConfig.INSTANCE.xpMultiplierPerLevel = val)
					.build());

			general.addEntry(entryBuilder.startIntField(
							Component.translatable("option.chiseledinfusion.catalyst_cost"),
							ModConfig.INSTANCE.lapisCostPerUpgrade)
					.setDefaultValue(1)
					.setMin(0)
					.setTooltip(Component.translatable("tooltip.chiseledinfusion.catalyst_cost"))
					.setSaveConsumer(val -> ModConfig.INSTANCE.lapisCostPerUpgrade = val)
					.build());

			general.addEntry(entryBuilder.startIntField(
							Component.translatable("option.chiseledinfusion.max_xp_cost"),
							ModConfig.INSTANCE.maxXpCost)
					.setDefaultValue(30)
					.setMin(0)
					.setTooltip(Component.translatable("tooltip.chiseledinfusion.max_xp_cost"))
					.setSaveConsumer(val -> ModConfig.INSTANCE.maxXpCost = val)
					.build());

			general.addEntry(entryBuilder.startStrField(
							Component.translatable("option.chiseledinfusion.catalyst_item_id"),
							ModConfig.INSTANCE.catalystItemId)
					.setDefaultValue("minecraft:lapis_lazuli")
					.setTooltip(Component.translatable("tooltip.chiseledinfusion.catalyst_item_id"))
					.setSaveConsumer(val -> ModConfig.INSTANCE.catalystItemId = val)
					.build());

			general.addEntry(entryBuilder.startIntField(
							Component.translatable("option.chiseledinfusion.scan_radius_horizontal"),
							ModConfig.INSTANCE.scanRadiusHorizontal)
					.setDefaultValue(2)
					.setMin(1)
					.setMax(8)
					.setTooltip(Component.translatable("tooltip.chiseledinfusion.scan_radius_horizontal"))
					.setSaveConsumer(val -> ModConfig.INSTANCE.scanRadiusHorizontal = val)
					.build());

			general.addEntry(entryBuilder.startIntField(
							Component.translatable("option.chiseledinfusion.scan_radius_vertical"),
							ModConfig.INSTANCE.scanRadiusVertical)
					.setDefaultValue(1)
					.setMin(0)
					.setMax(4)
					.setTooltip(Component.translatable("tooltip.chiseledinfusion.scan_radius_vertical"))
					.setSaveConsumer(val -> ModConfig.INSTANCE.scanRadiusVertical = val)
					.build());

			general.addEntry(entryBuilder.startBooleanToggle(
							Component.translatable("option.chiseledinfusion.clear_repair_cost"),
							ModConfig.INSTANCE.clearRepairCost)
					.setDefaultValue(true)
					.setTooltip(Component.translatable("tooltip.chiseledinfusion.clear_repair_cost"))
					.setSaveConsumer(val -> ModConfig.INSTANCE.clearRepairCost = val)
					.build());

			builder.setSavingRunnable(() -> {
				ModConfig.save();
				try {
					Minecraft client = Minecraft.getInstance();
					if (client != null && client.getSingleplayerServer() != null) {
						MinecraftServer server = client.getSingleplayerServer();
						server.reloadResources(server.getPackRepository().getSelectedIds());
					}
				} catch (Throwable ignored) {
				}
			});

			return builder.build();
		};
	}
}
