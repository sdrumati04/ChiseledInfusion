package sdrumati.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ServerAdvancementManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sdrumati.config.ModConfig;

import java.util.Optional;
import java.util.stream.Stream;

@Mixin(ServerAdvancementManager.class)
public abstract class ServerAdvancementManagerMixin {
	@Unique
	private static final Identifier VANILLA_ENCHANTING_TABLE_ADVANCEMENT =
			Identifier.fromNamespaceAndPath("minecraft", "recipes/decorations/enchanting_table");

	@Redirect(
			method = "<init>",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/core/HolderLookup$Provider;lookupOrThrow(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/HolderLookup$RegistryLookup;"
			)
	)
	private HolderLookup.RegistryLookup<Advancement> chiseledinfusion$filterVanillaRecipeAdvancements(
			HolderLookup.Provider provider,
			ResourceKey<? extends Registry<? extends Advancement>> key
	) {
		HolderLookup.RegistryLookup<Advancement> lookup = provider.lookupOrThrow(key);
		if (!ModConfig.INSTANCE.enableVanillaEnchantingTable) {
			return new HolderLookup.RegistryLookup.Delegate<Advancement>() {
				@Override
				public HolderLookup.RegistryLookup<Advancement> parent() {
					return lookup;
				}

				@Override
				public Optional<Holder.Reference<Advancement>> get(ResourceKey<Advancement> resourceKey) {
					if (resourceKey.identifier().equals(VANILLA_ENCHANTING_TABLE_ADVANCEMENT)) {
						return Optional.empty();
					}
					return parent().get(resourceKey);
				}

				@Override
				public Stream<Holder.Reference<Advancement>> listElements() {
					return parent().listElements()
							.filter(ref -> !ref.is(VANILLA_ENCHANTING_TABLE_ADVANCEMENT));
				}
			};
		}
		return lookup;
	}
}
