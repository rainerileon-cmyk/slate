# Slate Profile (`slate_profile`)

The player customization module: who you are in the game, and what you wear. It adds the **Profile** screen (the
middle chest of the Overhaul main menu, the account card of the Custom one) in the Custom and Overhaul layouts and
both styles; it has no Vanilla layout, being a menu vanilla does not have.

- **Looks** — a look is a skin and what goes with it: one cosmetic per slot (hat, face, shirt, trousers, back, a
  pet) and, per limb, another skin's arm or leg. Five looks show at once, the chosen one in the middle and two on
  either side; Select wears a look, Edit opens it, More renames, duplicates or deletes it.
- **Skins** — the account's own skin, or a PNG of 64 by 64 (wide or slim) picked from disk. It is kept with the look.
- **Accounts** — every Microsoft account that has played on this PC, with its looks. Choosing another account shows
  its looks; to play as it, sign in with the launcher.
- **Bio and picture** — a line about yourself and a profile picture (the face of your look, or a picture from disk).
- **In the game** — the look you wear is what you look like: in your own view and, on a server that has the module,
  for everyone else there who has it. Without it on the server, others see your account's skin.

Looks, the picture and the bio are kept on the PC, not in the game's folder, so every instance and version of the
game that has the module shows the same ones: `%APPDATA%\.slate\profile` on Windows, `~/Library/Application
Support/slate/profile` on macOS, `~/.slate/profile` elsewhere, one folder per account.

Not in it: signing in to another account from inside the game, and uploading a skin to Mojang (your account's skin
stays what it is; a look is Slate's).
