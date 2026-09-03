package sdrumati.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import sdrumati.ChiseledInfusion;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "chiseled_infusion.json");

	public int xpMultiplierPerLevel = 3;
	public int lapisCostPerUpgrade = 1;
	public int maxXpCost = 30;
	public String catalystItemId = "minecraft:lapis_lazuli";
	public int scanRadiusHorizontal = 2;
	public int scanRadiusVertical = 1;
	public boolean clearRepairCost = true;

	public static ModConfig INSTANCE = new ModConfig();

	public static void load() {
		if (CONFIG_FILE.exists()) {
			try (FileReader reader = new FileReader(CONFIG_FILE)) {
				ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
				if (loaded != null) {
					INSTANCE = loaded;
				}
			} catch (Exception e) {
				ChiseledInfusion.LOGGER.error("Failed to load configuration file, using defaults", e);
			}
		} else {
			save();
		}
	}

	public static void save() {
		try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
			GSON.toJson(INSTANCE, writer);
		} catch (IOException e) {
			ChiseledInfusion.LOGGER.error("Failed to save configuration file", e);
		}
	}

	public Item getCatalystItem() {
		try {
			Identifier id = Identifier.tryParse(catalystItemId);
			if (id != null) {
				Item item = BuiltInRegistries.ITEM.getValue(id);
				if (item != null && item != Items.AIR) {
					return item;
				}
			}
		} catch (Exception ignored) {
		}
		return Items.LAPIS_LAZULI;
	}
}
