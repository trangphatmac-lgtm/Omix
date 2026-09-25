package cn.omix.util.sigma;

import cn.omix.util.IMinecraft;
import cn.omix.Client;
import com.google.gson.JsonParser;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.display.FurnaceRecipeDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.DefaultResourcePack;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Uses the server's recipe displays; never reloads recipes, resources or data packs during rendering. */
public final class SigmaFurnaceRecipes implements IMinecraft {
    private final Map<Item, ItemStack> outputs = new HashMap<>();
    private final Map<Item, ItemStack> vanillaOutputs = new HashMap<>();
    private Object collections;
    private static CompletableFuture<List<VanillaRecipe>> vanilla;
    private record VanillaRecipe(String ingredient, String result, int count) {}

    public void refresh() {
        if (mc.player == null || mc.world == null) { clear(); return; }
        if (vanilla == null) {
            DefaultResourcePack pack = mc.getDefaultResourcePack();
            vanilla = CompletableFuture.supplyAsync(() -> readVanilla(pack), Util.getIoWorkerExecutor())
                    .exceptionally(error -> { Client.logger.debug("Could not read Jello furnace recipes", error); return List.of(); });
        }
        var current = mc.player.getRecipeBook().getOrderedResults();
        if (collections == current) return;
        collections = current; outputs.clear();
        var context = SlotDisplayContexts.createParameters(mc.world);
        for (var group : current) for (var entry : group.getAllRecipes()) {
            if (!(entry.display() instanceof FurnaceRecipeDisplay furnace)
                    || furnace.craftingStation().getStacks(context).stream().noneMatch(stack -> stack.isOf(Items.FURNACE))) continue;
            ItemStack result = furnace.result().getFirst(context);
            if (result.isEmpty()) continue;
            for (ItemStack ingredient : furnace.ingredient().getStacks(context)) if (!ingredient.isEmpty()) outputs.putIfAbsent(ingredient.getItem(), result.copy());
        }
    }
    public ItemStack result(ItemStack input) {
        if (input.isEmpty()) return ItemStack.EMPTY;
        ItemStack synced = outputs.get(input.getItem());
        if (synced != null) return synced.copy();
        // Like Jello's local SERVER_DATA fallback, without a RecipeManager reload on every draw.
        // Learned server displays take precedence; opening the furnace always resynchronizes estimates.
        if (vanilla == null || !vanilla.isDone()) return ItemStack.EMPTY;
        return vanillaOutputs.computeIfAbsent(input.getItem(), item -> {
            for (VanillaRecipe recipe : vanilla.getNow(List.of())) {
                boolean tag = recipe.ingredient.startsWith("#");
                Identifier id = Identifier.tryParse(tag ? recipe.ingredient.substring(1) : recipe.ingredient);
                if (id == null || !(tag ? input.isIn(TagKey.of(RegistryKeys.ITEM, id)) : Registries.ITEM.getId(item).equals(id))) continue;
                Identifier resultId = Identifier.tryParse(recipe.result);
                if (resultId != null) return Registries.ITEM.getOptionalValue(resultId).map(result -> new ItemStack(result, recipe.count)).orElse(ItemStack.EMPTY);
            }
            return ItemStack.EMPTY;
        }).copy();
    }

    private static List<VanillaRecipe> readVanilla(DefaultResourcePack pack) {
        List<VanillaRecipe> recipes = new ArrayList<>();
        pack.findResources(ResourceType.SERVER_DATA, "minecraft", "recipe", (id, input) -> {
            if (!id.getPath().endsWith(".json")) return;
            try (var reader = new InputStreamReader(input.get(), StandardCharsets.UTF_8)) {
                var object = JsonParser.parseReader(reader).getAsJsonObject();
                if (!object.has("type") || !"minecraft:smelting".equals(object.get("type").getAsString())) return;
                var ingredient = object.get("ingredient");
                if (!ingredient.isJsonPrimitive()) return;
                var result = object.getAsJsonObject("result");
                recipes.add(new VanillaRecipe(ingredient.getAsString(), result.get("id").getAsString(), result.has("count") ? result.get("count").getAsInt() : 1));
            } catch (Exception error) { Client.logger.debug("Skipping unsupported furnace recipe {}", id); }
        });
        return List.copyOf(recipes);
    }
    public void clear() { collections = null; outputs.clear(); vanillaOutputs.clear(); }
}
