package dev.fallingcloud.slate.profile.client;

import dev.fallingcloud.slate.core.client.DevHarness;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.profile.SlateProfile;
import dev.fallingcloud.slate.profile.Slot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * What a player has made of themselves, kept on the PC and not in the game's folder: the looks, which of them is
 * worn, the profile picture, the bio. Every instance and every version of the game on this PC that has the module
 * reads the same files, so a look made in one is there in all of them.
 *
 * <p>Where: {@code %APPDATA%\.slate\profile} on Windows, {@code ~/Library/Application Support/slate/profile} on
 * macOS, {@code ~/.slate/profile} elsewhere. One folder per account (by its UUID) with {@code profile.json}, the
 * skins of the looks ({@code looks/<id>.png}) and the picture ({@code picture.png}); {@code accounts.json} beside
 * the folders lists the accounts that have been here. Plain JSON and PNG, nothing of the game's own formats, so a
 * later version reads what an earlier one wrote.</p>
 */
public final class ProfileStore {

    /** A look as it is kept. {@code skin} says where the skin comes from: see {@link Look}. */
    public static final class LookEntry {
        public String id = "";
        public String name = "";
        public boolean slim = false;
        public String skin = "account";
        public Map<String, String> items = new LinkedHashMap<>();
    }

    public static final class AccountFile {
        public int format = 1;
        public String uuid = "";
        public String name = "";
        public String bio = "";
        /** Empty: the face of the look that is worn. Else the name of a picture in the account's folder. */
        public String picture = "";
        /** The id of the look that is worn; empty: none, the player looks as the account says. */
        public String worn = "";
        public List<LookEntry> looks = new ArrayList<>();
    }

    public static final class KnownAccount {
        public String uuid = "";
        public String name = "";
        public long lastSeen;
        /** Signed in with Microsoft when it was last here (an offline or development account otherwise). */
        public boolean microsoft;
    }

    public static final class AccountsFile {
        public List<KnownAccount> accounts = new ArrayList<>();
    }

    private static final Map<UUID, Account> OPEN = new HashMap<>();
    @Nullable private static JsonConfig<AccountsFile> accounts;

    /** The folder everything is kept in. The development harness keeps its made-up profile in the run folder instead. */
    public static Path root() {
        if (DevHarness.sampleData()) return SlatePlatform.get().gameDir().resolve("slate-sample-profile");
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        final String home = System.getProperty("user.home", ".");
        if (os.contains("win")) {
            final String appData = System.getenv("APPDATA");
            return Path.of(appData != null && !appData.isBlank() ? appData : home, ".slate", "profile");
        }
        if (os.contains("mac")) return Path.of(home, "Library", "Application Support", "slate", "profile");
        return Path.of(home, ".slate", "profile");
    }

    private static synchronized JsonConfig<AccountsFile> accountsFile() {
        if (accounts == null) accounts = JsonConfig.at(root().resolve("accounts.json"), AccountsFile.class, AccountsFile::new);
        return accounts;
    }

    /** Every account that has been here, the one last seen first. */
    public static List<KnownAccount> known() {
        final List<KnownAccount> out = new ArrayList<>(accountsFile().get().accounts);
        out.sort((a, b) -> Long.compare(b.lastSeen, a.lastSeen));
        return out;
    }

    /** Notes that an account is playing now. */
    public static void seen(final UUID uuid, final String name, final boolean microsoft) {
        accountsFile().update(f -> {
            KnownAccount k = null;
            for (final KnownAccount a : f.accounts) if (a.uuid.equalsIgnoreCase(uuid.toString())) k = a;
            if (k == null) { k = new KnownAccount(); k.uuid = uuid.toString(); f.accounts.add(k); }
            k.name = name;
            k.lastSeen = System.currentTimeMillis();
            k.microsoft = microsoft;
        });
    }

    /** The account's profile, read from disk the first time it is asked for. */
    public static synchronized Account account(final UUID uuid, final String name) {
        return OPEN.computeIfAbsent(uuid, u -> new Account(u, name));
    }

    /** One account's folder. */
    public static final class Account {

        private final UUID uuid;
        private final Path folder;
        private final JsonConfig<AccountFile> file;

        private Account(final UUID uuid, final String name) {
            this.uuid = uuid;
            this.folder = root().resolve(uuid.toString());
            this.file = JsonConfig.at(folder.resolve("profile.json"), AccountFile.class, AccountFile::new);
            if (file.get().uuid.isEmpty() || (!name.isEmpty() && !name.equals(file.get().name))) {
                file.update(f -> { f.uuid = uuid.toString(); if (!name.isEmpty()) f.name = name; });
            }
        }

        public UUID uuid() { return uuid; }

        public String name() { return file.get().name; }

        public Path folder() { return folder; }

        public AccountFile data() { return file.get(); }

        public void save() { file.save(); }

        public String bio() { return file.get().bio == null ? "" : file.get().bio; }

        public void bio(final String text) { file.update(f -> f.bio = text == null ? "" : text); }

        public List<Look> looks() {
            final List<Look> out = new ArrayList<>();
            for (final LookEntry e : file.get().looks) out.add(new Look(this, e));
            return out;
        }

        @Nullable
        public Look look(@Nullable final String id) {
            if (id == null || id.isEmpty()) return null;
            for (final LookEntry e : file.get().looks) if (e.id.equals(id)) return new Look(this, e);
            return null;
        }

        /** The look that is worn, or null when the player looks as the account says. */
        @Nullable
        public Look worn() { return look(file.get().worn); }

        public void wear(@Nullable final Look look) {
            file.update(f -> f.worn = look == null ? "" : look.id());
        }

        /** A new look, on the account's own skin, with nothing in its slots. It is kept at once. */
        public Look create(final String name, final String skin, final boolean slim) {
            final LookEntry e = new LookEntry();
            e.id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
            e.name = name;
            e.skin = skin;
            e.slim = slim;
            file.update(f -> f.looks.add(e));
            return new Look(this, e);
        }

        public Look duplicate(final Look of, final String name) {
            final Look copy = create(name, of.entry().skin, of.slim());
            copy.entry().items.putAll(of.entry().items);
            if (of.skinFile() != null && Files.isRegularFile(of.skinFile())) {
                try {
                    Files.createDirectories(folder.resolve("looks"));
                    Files.copy(of.skinFile(), folder.resolve("looks").resolve(copy.id() + ".png"), StandardCopyOption.REPLACE_EXISTING);
                    copy.entry().skin = "file";
                } catch (final IOException e) {
                    SlateProfile.LOGGER.warn("[Slate Profile] cannot copy the skin of {}: {}", of.name(), e.toString());
                }
            }
            save();
            return copy;
        }

        public void remove(final Look look) {
            file.update(f -> {
                f.looks.removeIf(e -> e.id.equals(look.id()));
                if (f.worn.equals(look.id())) f.worn = "";
            });
            try {
                Files.deleteIfExists(folder.resolve("looks").resolve(look.id() + ".png"));
            } catch (final IOException ignored) {}
        }

        /** The picture chosen for the profile, or null when it is the face of the look that is worn. */
        @Nullable
        public Path picture() {
            final String p = file.get().picture;
            if (p == null || p.isEmpty()) return null;
            final Path path = folder.resolve(p);
            return Files.isRegularFile(path) ? path : null;
        }

        /** Takes a picture file for the profile (copied into the account's folder); null goes back to the face. */
        public void picture(@Nullable final Path source) {
            if (source == null) {
                file.update(f -> f.picture = "");
                return;
            }
            try {
                Files.createDirectories(folder);
                final String name = source.getFileName().toString().toLowerCase(Locale.ROOT);
                final String kept = "picture" + (name.endsWith(".jpg") || name.endsWith(".jpeg") ? ".jpg" : ".png");
                Files.copy(source, folder.resolve(kept), StandardCopyOption.REPLACE_EXISTING);
                file.update(f -> f.picture = kept);
            } catch (final IOException e) {
                SlateProfile.LOGGER.warn("[Slate Profile] cannot keep the picture {}: {}", source, e.toString());
            }
        }
    }

    /**
     * A look: a skin and what is in the slots. The skin comes from one of three places: {@code account} is the skin
     * the account has with Mojang; {@code default:<name>} one of the game's own ({@code steve}, {@code alex}, ...);
     * {@code file} a picture the player gave, kept as {@code looks/<id>.png}.
     */
    public static final class Look {

        public static final List<String> DEFAULTS = List.of("steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri");

        private final Account account;
        private final LookEntry entry;

        private Look(final Account account, final LookEntry entry) {
            this.account = account;
            this.entry = entry;
            if (entry.items == null) entry.items = new LinkedHashMap<>();
        }

        LookEntry entry() { return entry; }

        public Account account() { return account; }

        public String id() { return entry.id; }

        public String name() { return entry.name == null ? "" : entry.name; }

        public void name(final String name) { entry.name = name; account.save(); }

        public boolean slim() { return entry.slim; }

        public void slim(final boolean slim) { entry.slim = slim; account.save(); }

        /** {@code account}, {@code default:<name>} or {@code file}. */
        public String skin() { return entry.skin == null || entry.skin.isEmpty() ? "account" : entry.skin; }

        public void skin(final String source) { entry.skin = source; account.save(); }

        /** Where the look's own skin picture is kept (whether or not there is one). */
        public Path skinFile() { return account.folder().resolve("looks").resolve(entry.id + ".png"); }

        /** Takes a skin picture for the look: copied into the account's folder. */
        public boolean upload(final Path png) {
            try {
                Files.createDirectories(skinFile().getParent());
                Files.copy(png, skinFile(), StandardCopyOption.REPLACE_EXISTING);
                entry.skin = "file";
                account.save();
                return true;
            } catch (final IOException e) {
                SlateProfile.LOGGER.warn("[Slate Profile] cannot keep the skin {}: {}", png, e.toString());
                return false;
            }
        }

        @Nullable
        public Cosmetic in(final Slot slot) {
            final Cosmetic c = Cosmetics.get(entry.items.get(slot.key()));
            return c != null && c.slot() == slot ? c : null;
        }

        public void put(final Slot slot, @Nullable final Cosmetic cosmetic) {
            if (cosmetic == null) entry.items.remove(slot.key());
            else entry.items.put(slot.key(), cosmetic.id());
            account.save();
        }

        /** Sets a slot without keeping it on disk: for trying something on, and taking it off again. */
        public void hold(final Slot slot, @Nullable final Cosmetic cosmetic) {
            if (cosmetic == null) entry.items.remove(slot.key());
            else entry.items.put(slot.key(), cosmetic.id());
        }

        /** Everything in the slots, in the order the slots are drawn in. */
        public List<Cosmetic> items() {
            final List<Cosmetic> out = new ArrayList<>();
            for (final Slot s : Slot.values()) {
                final Cosmetic c = in(s);
                if (c != null) out.add(c);
            }
            return out;
        }

        /** Changes with everything that changes what the look looks like: a key for what was made of it. */
        public String signature() {
            final StringBuilder b = new StringBuilder(entry.id).append('|').append(skin()).append('|').append(entry.slim);
            for (final Slot s : Slot.values()) b.append('|').append(entry.items.getOrDefault(s.key(), ""));
            if ("file".equals(skin())) {
                try {
                    b.append('|').append(Files.getLastModifiedTime(skinFile()).toMillis());
                } catch (final IOException ignored) {}
            }
            return b.toString();
        }
    }

    private ProfileStore() {}
}
