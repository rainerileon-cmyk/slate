import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data generator for Slate Building's toolbox content: item models, crafting + smithing recipes, the recipe-book
 * unlock advancements and the item tags. Run from the repository root (plain JDK, no dependencies):
 *
 * <pre>
 *   java tools/building/ToolboxData.java
 * </pre>
 *
 * Balance (survival): the toolbox is an early craft (3 copper + a chest). Tools follow vanilla's ladder - copper and
 * iron ingots, diamonds - with patterns that never collide with vanilla or the common chisel mods, and Netherite comes
 * from the smithing table (template + ingot, keeping enchantments and damage). Upgrades are mid-game: iron + redstone
 * around one themed ingredient. Materials use the loaders' shared {@code c:} tags so modded ingots work too.
 */
public final class ToolboxData {

    static final String NS = "slate_building";
    static final File DATA = new File("common/building/src/main/resources/data");
    static final File ASSETS = new File("common/building/src/main/resources/assets/slate_building");

    static final String[] TIERS = {"copper", "iron", "diamond", "netherite"};
    static final String[] TOOLS = {"trowel", "hammer", "brush", "blueprint", "square", "chisel"};
    static final String[] UPGRADES = {"reach", "capacity", "speed", "memory", "supply_link", "magnet", "efficiency"};

    /** Crafted tiers: material tag + the vanilla item used for the recipe-book unlock. */
    static final Map<String, String[]> MATERIAL = new LinkedHashMap<>();
    static {
        MATERIAL.put("copper", new String[]{"c:ingots/copper", "minecraft:copper_ingot"});
        MATERIAL.put("iron", new String[]{"c:ingots/iron", "minecraft:iron_ingot"});
        MATERIAL.put("diamond", new String[]{"c:gems/diamond", "minecraft:diamond"});
    }

    public static void main(final String[] args) throws IOException {
        if (!new File("common/building").isDirectory()) throw new IllegalStateException("run from the repository root");
        final List<String> tools = new ArrayList<>();
        final List<String> upgrades = new ArrayList<>();

        // ---- models
        model("toolbox", "generated");
        for (final String tier : TIERS) {
            for (final String tool : TOOLS) {
                final String id = tier + "_" + tool;
                tools.add(NS + ":" + id);
                model(id, tool.equals("blueprint") || tool.equals("square") ? "generated" : "handheld");
            }
        }
        for (final String u : UPGRADES) {
            upgrades.add(NS + ":" + u + "_upgrade");
            model(u + "_upgrade", "generated");
        }

        // ---- recipes
        shaped("toolbox", "misc", new String[]{" c ", "cXc"}, Map.of('c', tag("c:ingots/copper"), 'X', tag("c:chests/wooden")),
            "minecraft:copper_ingot", "has_copper_ingot");
        for (final Map.Entry<String, String[]> e : MATERIAL.entrySet()) {
            final String tier = e.getKey();
            final String m = tag(e.getValue()[0]);
            final String unlock = e.getValue()[1];
            final String stick = tag("c:rods/wooden");
            toolRecipe(tier, "trowel", new String[]{"  m", " mm", "s  "}, Map.of('m', m, 's', stick), unlock);
            toolRecipe(tier, "hammer", new String[]{" mm", " sm", "s  "}, Map.of('m', m, 's', stick), unlock);
            toolRecipe(tier, "brush", new String[]{" mw", " mm", "s  "}, Map.of('m', m, 's', stick, 'w', tag("minecraft:wool")), unlock);
            toolRecipe(tier, "blueprint", new String[]{"pmp", "mlm", "ppp"}, Map.of('m', m, 'p', item("minecraft:paper"), 'l', tag("c:gems/lapis")), unlock);
            toolRecipe(tier, "square", new String[]{"mmm", "p  ", "p  "}, Map.of('m', m, 'p', tag("minecraft:planks")), unlock);
            toolRecipe(tier, "chisel", new String[]{"  m", " m ", "s  "}, Map.of('m', m, 's', stick), unlock);
        }
        for (final String tool : TOOLS) {
            final String id = "netherite_" + tool;
            write(new File(DATA, NS + "/recipe/" + id + ".json"), """
                {
                  "type": "minecraft:smithing_transform",
                  "addition": %s,
                  "base": %s,
                  "result": {
                    "count": 1,
                    "id": "%s:%s"
                  },
                  "template": %s
                }
                """.formatted(tag("c:ingots/netherite"), item(NS + ":diamond_" + tool), NS, id,
                item("minecraft:netherite_upgrade_smithing_template")));
            unlock(id, "tools", "minecraft:netherite_ingot", "has_netherite_ingot");
        }
        final Map<String, String> key = new LinkedHashMap<>();
        key.put("reach", item("minecraft:spyglass"));
        key.put("capacity", tag("c:chests/wooden"));
        key.put("speed", item("minecraft:blaze_powder"));
        key.put("memory", item("minecraft:book"));
        key.put("supply_link", item("minecraft:ender_pearl"));
        key.put("magnet", item("minecraft:compass"));
        key.put("efficiency", tag("c:gems/diamond"));
        for (final String u : UPGRADES) {
            shaped(u + "_upgrade", "misc", new String[]{" r ", "iXi", " r "},
                Map.of('r', tag("c:dusts/redstone"), 'i', tag("c:ingots/iron"), 'X', key.get(u)), NS + ":toolbox", "has_toolbox");
        }

        // ---- tags
        tag(new File(DATA, NS + "/tags/item/toolboxes.json"), List.of(NS + ":toolbox"));
        tag(new File(DATA, NS + "/tags/item/tools.json"), tools);
        tag(new File(DATA, NS + "/tags/item/upgrades.json"), upgrades);
        // Unbreaking and Mending (and Curse of Vanishing, which includes this tag) apply to building tools.
        tag(new File(DATA, "minecraft/tags/item/enchantable/durability.json"), tools);
        System.out.println("wrote models, recipes, advancements and tags for " + tools.size() + " tools, " + upgrades.size() + " upgrades");
    }

    static void toolRecipe(final String tier, final String tool, final String[] pattern, final Map<Character, String> keys, final String unlockItem) throws IOException {
        final String id = tier + "_" + tool;
        final String criterion = "has_" + unlockItem.substring(unlockItem.indexOf(':') + 1);
        shapedTo(id, "equipment", pattern, keys, tool);
        unlock(id, "tools", unlockItem, criterion);
    }

    static void shaped(final String id, final String category, final String[] pattern, final Map<Character, String> keys,
                       final String unlockItem, final String criterion) throws IOException {
        shapedTo(id, category, pattern, keys, id.endsWith("_upgrade") ? "upgrades" : id);
        unlock(id, "misc", unlockItem, criterion);
    }

    static void shapedTo(final String id, final String category, final String[] pattern, final Map<Character, String> keys, final String group) throws IOException {
        final StringBuilder k = new StringBuilder();
        final List<Character> chars = new ArrayList<>(keys.keySet());
        chars.sort(null);
        for (int i = 0; i < chars.size(); i++) {
            k.append("    \"").append(chars.get(i)).append("\": ").append(keys.get(chars.get(i))).append(i + 1 < chars.size() ? ",\n" : "\n");
        }
        final StringBuilder p = new StringBuilder();
        for (int i = 0; i < pattern.length; i++) p.append("    \"").append(pattern[i]).append('"').append(i + 1 < pattern.length ? ",\n" : "\n");
        write(new File(DATA, NS + "/recipe/" + id + ".json"), """
            {
              "type": "minecraft:crafting_shaped",
              "category": "%s",
              "group": "%s:%s",
              "key": {
            %s  },
              "pattern": [
            %s  ],
              "result": {
                "count": 1,
                "id": "%s:%s"
              }
            }
            """.formatted(category, NS, group, k, p, NS, id));
    }

    /** The vanilla-style recipe-book unlock: the recipe is granted when the player first holds {@code item}. */
    static void unlock(final String id, final String folder, final String item, final String criterion) throws IOException {
        write(new File(DATA, NS + "/advancement/recipes/" + folder + "/" + id + ".json"), """
            {
              "parent": "minecraft:recipes/root",
              "criteria": {
                "%s": {
                  "conditions": {
                    "items": [
                      {
                        "items": "%s"
                      }
                    ]
                  },
                  "trigger": "minecraft:inventory_changed"
                },
                "has_the_recipe": {
                  "conditions": {
                    "recipe": "%s:%s"
                  },
                  "trigger": "minecraft:recipe_unlocked"
                }
              },
              "requirements": [
                [
                  "has_the_recipe",
                  "%s"
                ]
              ],
              "rewards": {
                "recipes": [
                  "%s:%s"
                ]
              }
            }
            """.formatted(criterion, item, NS, id, criterion, NS, id));
    }

    static void model(final String id, final String parent) throws IOException {
        write(new File(ASSETS, "models/item/" + id + ".json"), """
            {
              "parent": "minecraft:item/%s",
              "textures": {
                "layer0": "%s:item/%s"
              }
            }
            """.formatted(parent, NS, id));
    }

    static void tag(final File file, final List<String> values) throws IOException {
        final StringBuilder v = new StringBuilder();
        for (int i = 0; i < values.size(); i++) v.append("    \"").append(values.get(i)).append('"').append(i + 1 < values.size() ? ",\n" : "\n");
        write(file, """
            {
              "replace": false,
              "values": [
            %s  ]
            }
            """.formatted(v));
    }

    static String item(final String id) {
        return "{ \"item\": \"" + id + "\" }";
    }

    static String tag(final String id) {
        return "{ \"tag\": \"" + id + "\" }";
    }

    static void write(final File file, final String text) throws IOException {
        file.getParentFile().mkdirs();
        Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
    }

    private ToolboxData() {}
}
