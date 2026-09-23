# Hub protocol tests

Scripted TCP clients for a running Slate Multiplayer hub (`multiplayer-server.json`: `onlineMode=false`,
listener on 25580). No Minecraft needed on the client side.

- `hubproto.py` - the frame codec (`[len:4][kind:1][fields]`, FriendlyByteBuf encodings) and a tiny client.
- `hubtest.py` - end-to-end conformance run: friends, presence, DMs, typing, history, groups, invites,
  streams + frame blobs, stored DM media + MediaRequest, blocking, rate limits, session replacement.
  Prints PASS/FAIL per step; exits with "ALL PASS".
- `bot.py [seconds] [name] [uuid]` - "Alice": accepts friend requests, chats back, sends a PNG, invites,
  streams JPEG frames and watches other streams (counts received frames). Use a name that does not exist
  on Mojang (e.g. `zz_slatebot_a1`) so the client's name lookup falls back to the hub.

`python hubtest.py` / `python bot.py 300 zz_slatebot_a1 33333333-3333-3333-3333-333333333333`
