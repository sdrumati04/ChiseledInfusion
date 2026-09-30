package sdrumati.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sdrumati.config.ModConfig;

import java.util.Optional;
import java.util.stream.Stream;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
	@Unique
	private static final Identifier VANILLA_ENCHANTING_TABLE_RECIPE =
			Identifier.fromNamespaceAndPath("minecraft", "enchanting_table");

	@Redirect(
			method = "<init>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/core/HolderLookup$Provider;lookupOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/HolderLookup$RegistryLookup;"
			)
	)
	private HolderLookup.RegistryLookup<Recipe<?>> chiseledinfusion$filterVanillaRecipes(
			HolderLookup.Provider provider,
			ResourceKey<? extends Registry<? extends Recipe<?>>> key
	) {
		HolderLookup.RegistryLookup<Recipe<?>> lookup = provider.lookupOrThrow(key);
		if (!ModConfig.INSTANCE.enableVanillaEnchantingTable) {
			return new HolderLookup.RegistryLookup.Delegate<Recipe<?>>() {
				@Override
				public HolderLookup.RegistryLookup<Recipe<?>> parent() {
					return lookup;
				}

				@Override
				public Optional<Holder.Reference<Recipe<?>>> get(ResourceKey<Recipe<?>> resourceKey) {
					if (resourceKey.identifier().equals(VANILLA_ENCHANTING_TABLE_RECIPE)) {
						return Optional.empty();
					}
					return parent().get(resourceKey);
				}

				@Override
				public Stream<Holder.Reference<Recipe<?>>> listElements() {
					return parent().listElements()
							.filter(ref -> !ref.is(VANILLA_ENCHANTING_TABLE_RECIPE));
				}
			};
		}
		return lookup;
	}
}
