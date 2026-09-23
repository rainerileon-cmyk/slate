# Slate Multiplayer (`slate_multiplayer`)

Slate's social layer: friends, groups, presence, messages, invites, screen sharing and Simple Voice
Chat integration, with a **social hub** that any server running the module provides.

## How it connects

- **On a server that has the module**, everything works over the game connection: friends lists,
  presence and messages are stored by that server under `<server dir>/slate-hub/`.
- **A home hub** (`config/slate/multiplayer.json` → `homeHub`, e.g. `play.example.com:25580`) is the
  same hub reached over its own port, so it works from the title screen and stays connected while
  you play on other servers. Presence then tells friends which server you are on, and they get a
  **Join** button. Authentication uses the normal Mojang session handshake (offline mode is a hub option).
- Servers enable the port in `config/slate/multiplayer-server.json` (`enabled`, `port`, `onlineMode`,
  limits, history caps). Without a hub or a module-equipped server, the module shows the last known
  state from its local cache.

## What you get

- **Friends** — requests (by name, resolved through Mojang), accept/decline/remove/block, nicknames and
  notes, online/away/offline with what they are playing, sorted online-first, search. Player cards with
  skin, presence, mutual groups and actions.
- **Groups** — named sets of friends, each with a group chat, invites, and a "start voice group" button
  when Simple Voice Chat is installed.
- **Messages** — direct and group threads with heads, timestamps, day separators, typing indicators,
  unread counts, image attachments and clipboard paste; threads persist per hub in
  `config/slate/multiplayer/`.
- **Invites** — to your current server (a one-click join) or to a voice group.
- **Streams** — share your screen: frames are captured, downscaled and JPEG-encoded, then relayed to the
  friends who chose to watch, adaptively between 4 and 12 fps. Viewers get a full-screen viewer or a
  draggable picture-in-picture overlay with fps and latency.
- **Voice** — with Simple Voice Chat installed: speaking indicators on avatars, mute/deafen and per-player
  volume in Slate's UI, voice groups from friend groups. Voice clips for chat messages come from the
  same integration.
- **Notifications** — toasts for a friend coming online, a request, a message, an invite or a stream
  starting; click to open.

The Friends hub opens from the title screen, the pause menu, the Slate hub, the friends key (Controls →
Slate) or the action `slate_multiplayer:open_friends`. Dev-mode elements: `slate_multiplayer:friends_panel`
(compact online list for the title screen) and `slate_multiplayer:status_pill`; placeholder `{friends_online}`.
