# BetterInventory scout report (read-only; no files edited)

Paths below are relative to `C:/Users/leonr/Coding/MinecraftMods/Source/QOL/BetterInventory/` unless absolute.

## 1. What the mod is and the state of the repo

**Mod id and build**
- Mod id is `betterinventory` (`BetterInventory.MODID`). Display name "Better Inventory". MIT licence.
- Package is `dev.fallingcloud.betterinventory`.
- Built with NeoForge 21.1.219, MC 1.21.1, `net.neoforged.moddev` 2.0.42-beta, Java 21.
- `gradle.properties`: `mod_version=1.1.4`, `archives_base_name=betterinventory`.
- `build.gradle`: dev runtime JEI 19.39.0.372. `-PbiSelfTest` sets `-Dbetterinventory.selftest=true`, which runs the in-game shift-click self-test in `logic/SelfTest.java`.
- Build with `./gradlew build`.
- `neoforge.mods.toml` declares two mixin configs:
  - `betterinventory.mixins.json`
  - `betterinventory.bettercombat.mixins.json`, gated by a plugin.
- It has one optional dependency, `bettercombat` (ordering AFTER).

**Git**
- Branch `main`, remote `https://github.com/falling-colud/better-inventory.git`.
- HEAD is `5a96785 Inventory, item and tag additions` (2026-07-26), which is still `mod_version=1.0.0`.
- The working tree is heavily uncommitted: 88 files modified (+2821/−1025), `client/ZoomRender.java` deleted, and many untracked files. Untracked includes:
  - `ArmorSwapService`, `LoadoutFiller`, `LoadoutReturnService`, `SelfTest`, `ItemKinds`, `WornToolFloor`, `TravelersKnotItem`
  - `compat/BetterCombatMixinPlugin`, `mixin/compat/`, `PlayerHandItemMixin`, `CompatButtons`, `FtbSidebarCompat`, `InventorySwap`, `ClientTheme`, `BackpackLayer`, `ClientCombatBridge`
  - tier textures and recipes, and `tools/*` deploy scripts
- Everything 1.1.x is uncommitted.

**Jars**
- `build/libs/` holds 1.0.0, 1.0.1, 1.1.0, 1.1.1, 1.1.2, 1.1.3 and 1.1.4.
- `betterinventory-1.1.4.jar` was built 2026-09-15 20:41. No source file is newer than it, so 1.1.4 matches the current working tree.

**What is installed where (checked by md5)**
- The DF pack jar `.../packs/df/game/mods/betterinventory-neoforge-1.21.1.jar` has md5 `9578f662…`. That is identical to `build/libs/betterinventory-1.0.1.jar`, and its embedded toml says `version="1.0.1"`.
  - **DF runs an old 1.0.1**, which still has `ZoomRender`. The file date is Sep 16, but the contents are 1.0.1.
- The CUS2 pack (`cloud's-create-ultimate-selection-2-…/game/mods/`) has md5 `65e551ae…`, which is 1.1.4.
- `tools/deploy-cus2.sh` deploys 1.1.4 to the CUS2 server and then that pack only. Its header warns: "1.1.x moved every container slot index… a client and a server on different versions mis-index the inventory". So any slot change needs the server and client updated together.

**Stale Slate references**
- `Slate/common/config/src/main/resources/slate_config/df.json:79-80` points at `betterinventory-client.toml:zoom.enabled` and `zoom.scale`.
- The 1.1.4 `BetterInventoryConfig.CLIENT_SPEC` has no `zoom` section any more, only `inventory` and `hud`. The DF config file still has `[zoom]`, because DF is on 1.0.1.

**Features (README plus code)**
- It replaces the survival `InventoryScreen` with its own `BetterInventoryScreen`, which is 350×216.
- **Five tabs:** the player's own tab plus four backpack pages. Switching tabs physically swaps the 27 main slots with the stash or backpack.
- **5×9 gather hub.**
- **Tool rack (2×3):** hoe, shovel, pick1, pick2, axe, sword. It auto-switches for mining and combat, shows a HUD tool slot, and has a per-tab pickaxe-2 block list.
- **Offhand carousel (2×2), used with Alt + scroll.**
- **Two armor sets** with a swap bar.
- **Backpack slot:** backpack levels 1–5 are items, can be placed as a block, and render worn on the player. The backpack has 3 ability-upgrade slots: Magnet, Feeding, Refill, Void, Pickup.
- **Personal slots:** a stack upgrade (7 tiers, VANILLA or HUNDRED mode) and a crafting upgrade (2×2, 2×3 or 3×3 grid in the character panel).
- **Other features:**
  - slot locking by middle-click
  - trash slot
  - "mark entity safe" key
  - death-drop return (`LoadoutReturnService`, Corpse-friendly)
  - auto-refill
  - worn-tool speed floor
  - loot and trade tables
  - `ModCommands`
  - a "Vanilla Style" built-in resource pack
  - compat buttons for Curios, Cosmetic Armor, Aether and Accessories
  - FTB sidebar re-attach
  - Better Combat patches

## 2. The Alt "second hand" function (the offhand carousel)

**Keybinding** in `src/main/java/dev/fallingcloud/betterinventory/client/BetterInventoryClient.java` (`@EventBusSubscriber(modid = BetterInventory.MODID, value = Dist.CLIENT)`):
```java
public static final KeyMapping OFFHAND_SELECTOR = new KeyMapping(
        "key.betterinventory.offhand_selector",
        KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.betterinventory");
...
@SubscribeEvent
public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
    event.register(OFFHAND_SELECTOR);
    event.register(MARK_SAFE_ENTITY);   // default GLFW_KEY_SEMICOLON
}
...
event.registerAbove(VanillaGuiLayers.HOTBAR, BetterInventory.id("offhand_selector"), BetterInventoryHud::renderOffhandSelector);
```
- Lang (`en_us.json`): `"key.betterinventory.offhand_selector": "Offhand Carousel (hold + scroll)"`, category `"key.categories.betterinventory": "Better Inventory"`.
- Alt is detected through **`KeyMapping.isDown()`**, not raw GLFW. It is already rebindable in Controls. There is no `hasAltDown` or raw GLFW polling anywhere.

**What it does** (`client/ClientEvents.java`):
- `public static void onMouseScroll(InputEvent.MouseScrollingEvent event)`
  - Runs only when no screen is open and `OFFHAND_SELECTOR.isDown()`.
  - Sets `ClientHudState.pendingIndex = Math.floorMod(pendingIndex - (int) Math.signum(delta), 4)`.
  - **Cancels the event**, so the hotbar does not scroll.
- `public static void onClientTick(ClientTickEvent.Post event)`
  - Computes `down = OFFHAND_SELECTOR.isDown() && minecraft.screen == null`.
  - On press it seeds `pendingIndex = loadout.activeOffhand`.
  - On release it commits: if the index changed it calls `PacketDistributor.sendToServer(new BetterInventoryPayloads.SelectOffhand(ClientHudState.pendingIndex))` and optimistically sets `loadout.activeOffhand`.
  - It sets `ClientHudState.selectorOpen = down`, which drives the animation.
- **Holding Alt alone, without scrolling, already animates a vertical 4-cell column** out of the offhand box. This is `BetterInventoryHud.renderOffhandSelector(GuiGraphics, DeltaTracker)`, driven by `ClientHudState.progress`, which moves ±0.25 per tick.

**Packet** (`net/BetterInventoryPayloads.java`, C2S):
```java
public record SelectOffhand(int index) implements CustomPacketPayload {  // id betterinventory:select_offhand, VAR_INT
...
registrar.playToServer(SelectOffhand.TYPE, SelectOffhand.CODEC, (payload, context) -> {
    if (context.player() instanceof ServerPlayer player) {
        PlayerLoadout loadout = player.getData(ModAttachments.LOADOUT);
        int index = Math.floorMod(payload.index(), 4);
        if (index != loadout.activeOffhand) {
            loadout.activeOffhand = index;
            player.getInventory().offhand.set(0, loadout.offhandStore.getStackInSlot(index));
            loadout.dirty = true;
        }
    }
});
```
- Registrar version is `"1"`.
- On the server, every tick `CommonEvents.mirrorOffhand` keeps `offhandStore[activeOffhand]` identical to the real offhand.

**Conflicts with slate_building's hold-Alt-with-a-block wheel**
- Both mappings sit on `IN_GAME`. NeoForge lets several mappings share a key, so both register as down, and Controls shows them red.
- BI's scroll handler cancels `MouseScrollingEvent` while Alt is held. That eats the wheel's scroll unless the listener order or `receiveCanceled` says otherwise.
- **Left Alt is also used by Relics.** BI's dev `run/options.txt` has `key_key.relics.active_abilities_list:key.keyboard.left.alt`, and `relics-1.21.1-0.10.7.8.jar` is in the DF pack. Left Alt is effectively triple-booked in DF.

**Recommended rework (BI side)**
1. **Pick the default key by whether slate_building is present**, when the key is built. BI already uses `LoadingModList` in `compat/BetterCombatMixinPlugin`:
   ```java
   private static final boolean SLATE_BUILDING =
       net.neoforged.fml.loading.LoadingModList.get().getModFileById("slate_building") != null;
   ... SLATE_BUILDING ? <new key> : GLFW.GLFW_KEY_LEFT_ALT
   ```
   The new key could be a mouse side button (`InputConstants.Type.MOUSE`, `GLFW_MOUSE_BUTTON_4`) or a free letter key. DF has no `options.txt` in the pack, so conflicts can only be checked in-game in Controls.
2. **Migrate existing players once.** Vanilla writes every key to `options.txt`, so current players keep Alt otherwise.
   - On the first `ClientTickEvent.Post` after options load: if slate_building is loaded, the key is still `key.keyboard.left.alt`, and a new client-config flag (for example `inventory.offhandKeyMigrated`) is false, then call `OFFHAND_SELECTOR.setKey(newKey); KeyMapping.resetMapping(); mc.options.save();` and set the flag.
3. **Update the lang text** "Offhand Carousel (hold + scroll)". Scrolling stays; only the held key changes.
4. **Port the same change to Fabric if that port is revived.** There the scroll hook is the `mixin/MouseScrollMixin` on `MouseHandler.onScroll` HEAD, which calls `ClientEvents.onMouseScroll(double)`.

## 3. The slot system (how the extra slots work)

**It is a full custom menu, not a mixin into `InventoryMenu`.**
- `ClientEvents.onScreenOpening(ScreenEvent.Opening)` cancels vanilla `InventoryScreen` and sends C2S `OpenLoadout`. This happens only when the player is not creative and `!InventorySwap.vanillaPreferred()`, which reads the client config `USE_VANILLA_INVENTORY`.
- The server runs `BetterInventoryMenu.open(ServerPlayer)`, which calls `player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BetterInventoryMenu(id, inv), …))`.
- The MenuType is `ModMenus.LOADOUT` (`"loadout"`). The screen is registered in `BetterInventoryClient.onRegisterScreens`.
- Vanilla `player.inventoryMenu` is untouched. Extra slots are invisible in creative, in the vanilla screen, and to other mods.
- The only mixins are stack-size, count-codec, combat, hand-render, `ServerPlayerGameMode` and `ContainerCursor`.

**Data: one NeoForge data attachment**
```java
// registry/ModAttachments.java
public static final Supplier<AttachmentType<PlayerLoadout>> LOADOUT = ATTACHMENTS.register(
        "loadout",
        () -> AttachmentType.serializable(PlayerLoadout::new).copyOnDeath().build());
```
- The attachment id is `betterinventory:loadout`.
- `inv/PlayerLoadout implements INBTSerializable<CompoundTag>` holds NeoForge `ItemStackHandler`s:

| Field | Size / type |
|---|---|
| `tools` | 6 |
| `offhandStore` | 4 |
| `armorStore` | 4 |
| `gather` | `BigStackHandler` 45 |
| `backpacks` | 1 |
| `packUpgrades` | virtual 3, stored on the backpack's `BACKPACK_UPGRADES` component |
| `personal` | 2 (slot 0 `StackUpgradeItem`, slot 1 `CraftingUpgradeItem`) |
| `mainStash` | 27 |

- Also holds `activeTab`, `activeOffhand`, `craftingOpen`, `TabSettings`, `safeEntities`, and `public boolean dirty`.
- Each handler's `onContentsChanged` sets `dirty = true`.
- `serializeNBT` writes keys such as `"tools"`, `"offhand_store"`, `"armor_store"`, `"gather"`, `"backpacks"`, `"personal"` and `"main_stash"`.
- `deserializeNBT` reads them, then runs migrations and `ensureSize(handler, n)`.

**Menu slot indices** (`menu/BetterInventoryMenu.java`, hard-coded constants):
```
SLOT_HOTBAR 0 | SLOT_MAIN 9 | SLOT_GATHER 36 | SLOT_ARMOR 81 | SLOT_ARMOR_STORE 85 | SLOT_OFFHAND 89
SLOT_TOOLS 93 | SLOT_PERSONAL 99 | SLOT_BACKPACK 101 | SLOT_PACK_UPGRADES 102 | SLOT_CRAFT 105
SLOT_CRAFT_RESULT 114 | SLOT_TRASH 115 (deliberately last) | SLOT_END 116
```
- Handler-backed slots are `net.neoforged.neoforge.items.SlotItemHandler`, with `setBackground(InventoryMenu.BLOCK_ATLAS, IconSlots.X)`.
- Icons come from `menu/slots/IconSlots.java` as `BetterInventory.id("item/empty_slot_*")`, meaning textures under `textures/item/`.
- Offhand slots go through `menu/slots/OffhandRouteContainer`.
- Other menu mechanics:
  - `quickMoveStack` sends equipment through `tryEquip(ItemStack)`.
  - Anything at index ≥ `SLOT_GATHER` falls into the "gather hub / equipment → main, then hotbar" branch.
  - `addDataSlot` carries `activeTab` and `craftingOpen`.

**Sync** (`logic/SyncService.java`)
- The whole attachment goes as NBT through S2C `SyncLoadout(CompoundTag)` (`ByteBufCodecs.TRUSTED_COMPOUND_TAG`).
- `CommonEvents.onPlayerTick` calls `syncIfDirty` every tick, and `syncNow` runs on login, respawn and dimension change.
- The client applies it with `applyClientSync(Player, CompoundTag)`, which calls `loadout.deserializeNBT` on the client player's attachment.
- **Result: the client-side `player.getData(ModAttachments.LOADOUT)` always mirrors the server.**

**Death**
- `CommonEvents.onPlayerDrops(LivingDropsEvent)` runs `dropAll(event, player, handler, LoadoutReturnService.SECTION_*)`, which stamps a `LoadoutOrigin` component on each stack.
- `LoadoutReturnService.route(...)` has a `switch` over the section constants, plus `stripHandlers`, to send recovered items back.
- `onClone` runs at LOWEST priority.

**Screen** (`client/screen/BetterInventoryScreen.renderBg`)
- It is built from atlas panels in `textures/gui/inventory.png` (512×256), positioned by `BetterInventoryLayout`. This layout is mirrored by `tools/preview.py`, and both must stay in sync.
- `PANEL_1X1 = {176,112,24,24}` already exists and is used for the trash at `TRASH_X=299, TRASH_Y=HOTBAR_Y=192`.
- **Free spot for a toolbox cell:** the left column on the hotbar row (x 0..42, y 192..216) is empty. The rack 2×3 ends at `SIDE_BOTTOM_Y=185`, and the `InventorySwap` button hangs 7px *below* the panel.
  - A 1×1 at `TOOLBOX_X = LEFT_X + (42-24)/2 = 9`, `TOOLBOX_Y = HOTBAR_Y` mirrors the trash symmetrically.
- The top-left 2×2 `COMPAT_Y` panel holds up to 4 compat buttons. DF has Curios and Cosmetic Armor, so 2 cells are used there. It holds buttons, not slots.
- `registry/ModTags.AUX_UPGRADES` (`betterinventory:aux_upgrades`) is declared but unused. It is stale; `personal[1]` now takes `CraftingUpgradeItem`.

## 4. Cleanest way to add a toolbox slot (BI side)

1. **Detect slate_building and recognise toolboxes without a compile dependency on slate:**
   ```java
   public static final boolean SLATE_BUILDING = ModList.get().isLoaded("slate_building"); // or LoadingModList
   public static final TagKey<Item> TOOLBOXES = TagKey.create(Registries.ITEM,
           ResourceLocation.fromNamespaceAndPath("slate_building", "toolboxes"));
   ```
   slate_building ships `data/slate_building/tags/item/toolboxes.json`.
2. **Add a handler to `PlayerLoadout`**, following the `armorStore` pattern:
   ```java
   public final ItemStackHandler toolbox = new ItemStackHandler(1) {
       @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(TOOLBOXES); }
       @Override public int getSlotLimit(int slot) { return 1; }
       @Override protected void onContentsChanged(int slot) { dirty = true; }
   };
   ```
   - Add `tag.put("toolbox", toolbox.serializeNBT(provider))` and `toolbox.deserializeNBT(provider, tag.getCompound("toolbox"))`.
   - Add `ensureSize(toolbox, 1)`. Old saves load fine because an empty compound keeps the size.
3. **Menu:** append the slot after the trash so no existing index moves: `SLOT_TOOLBOX = SLOT_TRASH + 1` (116), `SLOT_END = 117`.
   - **Always add it** and gate it with `isActive() { return SLATE_BUILDING; }` plus `mayPickup`/`mayPlace`. Indices stay identical whatever the mod set, and inactive slots are neither drawn nor clickable.
   - Use `setBackground(BLOCK_ATLAS, BetterInventory.id("item/empty_slot_toolbox"))`.
   - In `tryEquip`, add a case for `stack.is(TOOLBOXES)` into `SLOT_TOOLBOX`.
   - The trash-last invariant is preserved, because shift-click ranges never reach 116.
4. **Screen:** add `TOOLBOX_X/Y` to `BetterInventoryLayout` and `tools/preview.py`. Call `panel(graphics, PANEL_1X1, TOOLBOX_X, TOOLBOX_Y)` only when `SLATE_BUILDING`. Add a tooltip in `renderControlTooltips`.
5. **Death and return:**
   - `LoadoutReturnService.SECTION_TOOLBOX = "toolbox"` plus a `case` in `route`, like `SECTION_ARMOR`.
   - `stripHandler(loadout, loadout.toolbox)`.
   - `dropAll(event, player, loadout.toolbox, SECTION_TOOLBOX)` in `onPlayerDrops`.
   - Probably mark it structural (see `isStructural`) if the toolbox gates other slots.
6. **Sync** is automatic through `dirty` → `SyncLoadout`. Bump `mod_version`. It is a protocol change, so deploy server and client together, as `deploy-cus2.sh` warns.

## 5. How slate_building should read the slot

- BI currently has **no public API**: no `api` package and no capability exposed.
- **Unused scaffolding:** `platform/Platform`, `PlayerData`, `Services`, `ItemStore`, `AccessorySlots` and `NeoForgePlatform` exist, and `NeoForgePlatform` is registered in `META-INF/services`. Nothing calls `Services.platform()`.
- **Recommendation:** add a tiny BI class whose signatures use vanilla types only, for example `dev.fallingcloud.betterinventory.api.BetterInventoryApi`:
  ```java
  public static ItemStack toolbox(Player p)            { return p.getData(ModAttachments.LOADOUT).toolbox.getStackInSlot(0); }
  public static boolean setToolbox(Player p, ItemStack s) // server-side, validates + marks dirty
  public static boolean hasToolboxSlot()              // == SLATE_BUILDING
  ```
  - It works on both sides, because the client mirror is synced.
- **On the slate_building side**, write a reflective bridge in its compat package that copies Slate's existing pattern: `common/multiplayer/.../voice/VoiceStatus.java` with `SvcAdapter`, and `IrisBridge` / `SodiumBridge`.
  - Gate with `SlatePlatform.get().isModLoaded("betterinventory")`.
  - Look up once with `Class.forName("dev.fallingcloud.betterinventory.api.BetterInventoryApi").getMethod("toolbox", Player.class)` and cache it as a MethodHandle.
  - Wrap every call in try/catch and fall back to `ItemStack.EMPTY`.
  - This keeps slate's `common/` loader-agnostic and needs no compile dependency. On Fabric the class is absent, so the bridge reports unavailable.
- **Toolbox lookup order in slate_building:** BI toolbox slot, then offhand, then hotbar, then main inventory. This way it still works without BI, in creative, and on the vanilla screen.
- Direct attachment access by id (`NeoForgeRegistries.ATTACHMENT_TYPES` / `betterinventory:loadout`) is possible. It would still need reflection into `PlayerLoadout.toolbox`, and it is NeoForge-only, so the API class is cleaner.

## 6. Fabric port

- `ports/fabric-1.21.1/` is a **separate Gradle project with a full, separate copy of the code**.
  - It uses fabric-loom 1.17.14, loader 0.19.3, Fabric API 0.116.13+1.21.1 and Mojang mappings.
  - It has its own `fabric/LoadoutHolder` plus `mixin/PlayerLoadoutMixin` (a player-NBT holder instead of an attachment), `net/Net.java`, and `inv/ItemStore`. `MouseScrollMixin` stands in for the scroll event.
  - Notes are in `PORT-NOTES.md`.
- **Not maintained:**
  - `mod_version=1.0.0`, jar `build/libs/betterinventory-fabric-mc1.21.1-1.0.0.jar` from 2026-08-01, last source edit 2026-08-01.
  - It lacks `armorStore`, `packUpgrades`, trash, `LoadoutReturnService` and more, and still has 4 backpack slots and `ZoomRender`.
  - `ports/` is in BI's `.gitignore` and has no git repo of its own, so **the Fabric port is not version-controlled.**
- Slate's `docs/AGENT-BRIEF.md:48` cites it only as a working loom + mojmap example.

## 7. Existing soft-dependency patterns in BI

- `ModList.get().isLoaded(...)`:
  - `client/CompatButtons.entries()` checks `curios`, `cosmeticarmorreworked`, `aether` and `accessories`, then calls each mod's payload class or client method by reflection, wrapped in try/catch with a `broken` flag.
  - `client/FtbSidebarCompat` checks FTB Library.
  - `compat/CuriosCompat` checks `curios`, then does reflective `CuriosApi.getCuriosInventory`.
  - `platform/NeoForgePlatform.isModLoaded` exists but is unused.
- `LoadingModList.get()` for a mixin plugin: `compat/BetterCombatMixinPlugin`, attached to `betterinventory.bettercombat.mixins.json`, plus an optional `bettercombat` dependency in the toml.
- Function-pointer hooks set from client setup: `CombatService.engagedClient` and `CombatService.betterCombatLookup = ClientCombatBridge::substituteForBetterCombat`.
- **There is no existing reference to `slate` or `slate_building` anywhere in BI.**