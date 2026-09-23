# Slate Chat (`slate_chat`)

The in-game chat rebuilt on Slate, in both skins. It replaces the Chatterbox mod (same ideas, same
fallback tokens, so players without the mod still see readable text).

- **Grouped messages** by sender with player heads and timestamps, cozy or compact density, a
  translucent panel with configurable opacity, width and height, unread badge while the chat is closed,
  slide-in animation, mention highlighting with a ping sound.
- **Channels** above the input: Global (server chat), System, and, when Multiplayer is installed, one
  tab per open direct or group conversation with unread counts.
- **Media** — images and GIFs (a GIF library with Tenor search, favourites and recents), voice clips
  (hold to record; needs Simple Voice Chat), pasted clipboard images, video links (opens an embedded
  browser when MCEF is installed), link previews; a zoom/pan image viewer. Media travels over Slate's
  own chunked transfer through the server, never through a third party, except link embeds you opt into.
- **Emotes** — `:smile:`, `:fire:`, `:creeper:` and friends render as pixel emotes; a picker inserts them.
- **History and search** — recent messages are kept per server and restored when you rejoin; Ctrl+F
  filters the visible list. Hover a message for copy/reply/open-link actions. Typing indicators show who
  is writing.

Settings live in `config/slate/chat.json` (look, density, timestamps, grouping, mentions, embeds, media
size caps, history size) and in the Chat page of Slate Config; relay limits for servers are in
`config/slate/chat-server.json`. The Tenor key for GIF search goes in `config/slate/media.json`.
