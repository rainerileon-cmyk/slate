# Slate Building overnight build — brief for every agent

Read, in this order: `docs/building-design.md` (THE contract: model, APIs, ownership), `DESIGN.md` (suite
contract; its Core section has errors listed in building-design §0), `docs/AGENT-BRIEF.md` (house rules).
Scout maps with exact signatures are in `docs/building-maps/` (`core-api.md`, `build-wiring.md`, `mc-api.md`,
`config-module.md`, `df-pack-variants.md`, `chisel-compat.md`, `betterinventory.md`) — use them instead of
re-discovering things, and verify anything you rely on against the real jars (`javap`).

## Your working copy
You work ONLY in the git worktree the lead gave you (path in your task), on its branch. Never touch
`C:/Users/leonr/Coding/MinecraftMods/Source/UI/Slate` itself (the user has uncommitted work there) and never
another agent's worktree. Commit on your branch when done (and at useful checkpoints):
`git add -A && git -c user.name=Claude -c user.email=noreply@anthropic.com commit -m "<area>: <what>"` ending with the line
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not merge, rebase or push.

## Building (16 GB RAM machine, up to ~10 agents at once)
Never call `gradlew` directly. Use the shared semaphore (max 3 concurrent builds, JAVA_HOME handled):
```
SB=C:/Users/leonr/AppData/Local/Temp/claude/C--Users-leonr-Coding-MinecraftMods-Source-UI-Slate/84b04579-fab4-4717-a704-804ffaef9973/scratchpad
bash $SB/gbuild.sh "$PWD/neoforge" :building:compileJava -q
bash $SB/gbuild.sh "$PWD/fabric"   :building:compileJava -q
```
(`:building:build` before you finish; add `:core:build` / `:config:build` when you touched those.) It may wait
for a slot; that is normal. A build can take several minutes — run it with a long Bash timeout (600000 ms) or in
the background. Both loaders must be green before you commit. Asset-download SSL errors: just retry.

Dev client (only when your part is complete and you need to SEE it): ONE client at a time machine-wide, via
`bash $SB/grun.sh "$PWD/neoforge" :dev:runClient -PautoWorld=true -PbuildingHarness=<scenario> -PautoQuit=true`
— always with a harness scenario that quits by itself (see `BuildingHarness`). Screenshots land in
`neoforge/dev/run/screenshots/`. Look at them with the Read tool. Never kill java processes you did not start.

## Rules
- Only edit files you own (building-design §12). If you need something from another owner's area, code
  against the API in the design doc; if it is missing, add a clearly marked minimal version in YOUR package and
  mention it in your report — do not edit theirs.
- Translations: `common/building/src/main/resources/assets/slate_building/lang/parts/<your-agent>.json`
  (merged into `en_us.json` at build time). Every user-visible string is translatable.
- Both skins (`Theme.current().isVanilla()`), hover/press/focus/disabled states, keyboard, tooltips where
  useful, `Theme.motion()` honoured. Polish matters: this is judged on how clean and animated it feels.
- No `net.neoforged`/`net.fabricmc` in `common/`; client classes never reachable on a dedicated server.
- Write real code, not TODOs. If something is impossible, build the closest working thing and explain.
- Do not edit `common/core/.../gfx/SlateDraw.java` or `common/menu/**` (except F: `MenuClient.java`).

## Report (your final message, under a page)
What exists (files/classes), what works, what is stubbed or untested, APIs you added or assumed from other
owners, build results for both loaders, commit hash.
