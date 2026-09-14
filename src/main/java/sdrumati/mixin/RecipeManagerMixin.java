package sdrumati.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import sdrumati.config.ModConfig;

import java.util.List;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
	@ModifyVariable(
			method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
			at = @At("HEAD"),
			argsOnly = true
	)
	private RecipeMap chiseledinfusion$filterVanillaRecipes(RecipeMap recipeMap) {
		if (!ModConfig.INSTANCE.enableVanillaEnchantingTable) {
			List<RecipeHolder<?>> filtered = recipeMap.values().stream()
					.filter(holder -> !isVanillaEnchantingTableRecipe(holder))
					.toList();
			return RecipeMap.create(filtered);
		}
		return recipeMap;
	}

	@Unique
	private static boolean isVanillaEnchantingTableRecipe(RecipeHolder<?> holder) {
		Identifier id = holder.id().identifier();
		return id.getNamespace().equals("minecraft") && id.getPath().equals("enchanting_table");
	}
}
