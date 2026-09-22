# Working on Slate — brief for every task

Read `DESIGN.md` first (the contract), then this. The code you build on is in `common/core`
(read it — `Slate`, `SlateClient`, `theme/*`, `gfx/*`, `widget/*`, `screen/*`, `layout/*`, `net/*`).

## Ground rules
1. **Both loaders must stay green.** Verify with, from your working copy:
   `cd neoforge && ./gradlew.bat :<module>:build --no-daemon -q` and
   `cd fabric && ./gradlew.bat :<module>:build --no-daemon -q` (plus `:core:build` if you touched core).
   Full: `./gradlew.bat build --no-daemon -q` in each. Builds take 1–4 min; do not run two Gradle builds
   of the same loader dir at once. On Windows Git Bash the wrapper is `./gradlew.bat`.
2. **Do not change existing public signatures in Core** (other tasks compile against them right now).
   Adding methods/classes/files is fine. If you must change one, note it prominently in your report.
3. **Ownership** — only touch the paths you own (listed in your task); Core additions go in NEW files
   under the packages named in your task. Never edit `Slate.java`, `SlateClient.java`, `Icon.java`,
   `DESIGN.md` or another module's folder unless your task says so.
4. **Translations**: put your keys in your own namespace lang file
   (`assets/<your namespace>/lang/en_us.json`) — Minecraft merges lang files across namespaces, and a
   shared file would conflict between tasks. Core tasks: `slate` (polish) / `slate_editor` (editor).
5. **No split packages, no loader classes in `common/`, no `javax.annotation`.** Soft deps behind
   `SlatePlatform.get().isModLoaded(id)` in a class that is only loaded after the check
   (see DESIGN.md hard rules). Payload ids use the `slate` namespace and are registered in `init()`.
6. **Verify vanilla APIs against the real jar, not memory.** Mapped Minecraft 1.21.1 (mojmap) is at
   `C:/Users/leonr/Coding/MinecraftMods/Source/Ports/_template/fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/*/minecraft-merged-*-v2.jar`
   — `javap -cp <jar> net.minecraft.client.gui.screens.TitleScreen` prints signatures; `unzip -l` finds classes.
   NeoForge classes: `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/21.1.247/**/*.jar`.
   Vendored mod jars for soft deps: `C:/Users/leonr/Coding/MinecraftMods/Source/libs/` (sodium.jar is
   the extracted inner jar with the config API; voicechat.jar; mcef.jar; iris.jar).
7. **Both skins, always.** Every screen/widget you make must look right with `Theme.current().isVanilla()`
   true and false. Every interactive thing: hover, press, focus, disabled, tooltip where useful, keyboard.
8. **Both sides.** Anything that references `net.minecraft.client.*` must never be loaded on a dedicated
   server: keep client code in client-only classes reached only from `initClient()`/client events.
9. **Write real code, not TODOs.** Every feature in your task list should work end to end. If something
   is genuinely impossible, implement the closest thing that works and explain in the report.
10. **Commit when done** on your branch (`git add -A && git commit -m "..."` in your working copy). The
    lead merges branches; do not merge or rebase yourself. Commit intermediate progress too.
11. Report at the end: what exists (files/classes), what works, what is stubbed, any Core signature
    you had to change, any icons you wish existed (see `gfx/Icon.java` for what exists), and the exact
    build results. Keep it under a page.

## Reference material
- Chatterbox (the Discord-style chat mod the Chat module and the media stack are ported from):
  `C:/Users/leonr/Coding/MinecraftMods/Source/QOL/Chatterbox/src/main/java/dev/fallingcloud/chatterbox/`
  (NeoForge-only; port to Core's loader-agnostic APIs: `SlateNetwork`, `SlateEvents`, `SlatePlatform`,
  `core.net.blob.*` replaces its payloads/relay/upload).
- DF modpack (the target): `C:/Users/leonr/AppData/Roaming/CloudLauncher/default/packs/df/game/`
  (`mods/`, `config/`, `options.txt`, `screenshots/`, `servers.dat`). Read-only for you.
- BetterInventory Fabric port (working loom + mojmap example): `C:/Users/leonr/Coding/MinecraftMods/Source/QOL/BetterInventory/ports/fabric-1.21.1/`.
- NeoForge 1.21.1 config screen recipe: `ModContainer.registerExtensionPoint(IConfigScreenFactory.class, ...)`,
  `ConfigurationScreen`, `ModConfigSpec`, `ConfigTracker.INSTANCE` (for reading other mods' TOML configs).
