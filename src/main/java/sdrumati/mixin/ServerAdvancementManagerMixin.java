package sdrumati.mixin;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sdrumati.config.ModConfig;

import java.util.Map;

@Mixin(ServerAdvancementManager.class)
public abstract class ServerAdvancementManagerMixin {
	@Inject(
			method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
			at = @At("HEAD")
	)
	private void chiseledinfusion$filterVanillaRecipeAdvancements(
			Map<Identifier, Advancement> map,
			ResourceManager resourceManager,
			ProfilerFiller profilerFiller,
			CallbackInfo ci
	) {
		if (!ModConfig.INSTANCE.enableVanillaEnchantingTable) {
			map.remove(Identifier.fromNamespaceAndPath("minecraft", "recipes/decorations/enchanting_table"));
		}
	}
}
