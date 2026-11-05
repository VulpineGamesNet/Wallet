# Wallet — Minecraft 1.12.2 Forge port

A standalone Gradle build for Forge `1.12.2-14.23.5.2860` (Java 8 bytecode). The
stonecutter tree at the repository root (1.20.1 Forge, 1.21.1 NeoForge) is left
untouched and builds exactly as before; nothing here is shared with it.

## Why a separate build

1.12.2 needs a different toolchain (RetroFuturaGradle on Java 8, MCP names
reobfuscated to SRG) and a different Minecraft API — no Brigadier, no data
components, no `ResourceKey` dimensions. Putting it behind stonecutter
conditionals would have touched almost every file of the modern code, so the
port is a parallel source set that keeps the modern **database schema, SQL,
config keys and command syntax** identical.

## Build

```bash
cd legacy/1.12.2-forge
./gradlew build
```

The first run decompiles Minecraft (~2 minutes). Outputs in `build/libs/`:

| jar | use |
|---|---|
| `wallet-<ver>-forge+1.12.2.jar` | the server mod (reobfuscated, drivers shaded) |
| `wallet-<ver>-forge+1.12.2-dev.jar` | deobfuscated, for compiling addons such as WalletAuction |

Shaded and relocated under `net.vulpinegames.wallet.shadow.*`: HikariCP 4.0.3
(last Java 8 line), MariaDB Connector/J 3.4.1 (used for `backend: "mysql"`;
Windows-only waffle SSPI excluded) and log4j-slf4j-impl 2.8.1 (routes Hikari's
logging into Minecraft's log4j). sqlite-jdbc 3.46.1.3 is shaded **without**
relocation because its native library binds JNI symbols to `org.sqlite`. Packs
like RLCraft ship no JDBC driver, which is why everything is in the jar.

## Server-side only

`acceptableRemoteVersions = "*"`: vanilla/unmodded clients can join. All text
uses plain chat components and vanilla items.

## Differences from the modern builds

- **Coins**: 1.12.2 has no `CustomModelData` component. A coin is a
  `minecraft:gold_nugget` with an **int NBT tag named `CustomModelData`** holding
  the same ids (710000, 719999, 719100, 719010, 719001 for 10000/1000/100/10/1),
  plus a display name and lore. A resource pack can key a model on that tag with
  OptiFine CIT (`nbt.CustomModelData=710000`); without one the coins look like
  gold nuggets with a coloured name.
- **Shop keys**: `wallet_shops.sign_key` and `chest_pos` are `x_y_z_<dimension id>`
  using the numeric 1.12 dimension id (`0`, `-1`, `1`) instead of a resource key.
  1.12.2 shops are therefore separate from shops created on other versions even
  when they share a database (they never do in practice).
- **Item templates**: `[{id, meta, count, nbt|null}]`. With `strictItemMatch`
  (default) and an `nbt` key, a stack must match item, meta and NBT exactly;
  otherwise item id plus meta for items with subtypes.
- **Commands**: `/wallet` is a `CommandBase`; tab completion is manual.
  Addons cannot merge into a Brigadier tree, so they call
  `WalletApi.registerSubcommand(name, WalletSubcommand)` in pre-init and Wallet
  dispatches `/wallet <name> ...` to them (see `api/WalletSubcommand.java`).
- **No pistons**: Forge 1.12.2 has no piston event. Chests cannot be pushed;
  a sign popped off by a piston leaves its chest protected until an admin
  removes the shop.
- **Sign waxing** does not exist on 1.12; sign text is rewritten on creation.

## Optional protections (1.12.2 only, off by default)

Two `config/wallet.json` keys exist only in this build and default to `false`,
so behaviour matches the modern builds unless a server opts in:

| key | effect when `true` |
|---|---|
| `protectShopChests` | only the shop owner and ops can open a player shop's chest |
| `protectShopNeighbors` | blocks placing a block with a tile entity (hopper, chest…) next to someone else's shop chest |

RLCraft turns both on because it has no claims mod. ATM10 and DeceasedCraft do
not run this build and are unaffected.

## Not ported

- Loot-table coin injection (no coin loot on 1.12).
- `ShopDao.getAllSignKeys` (unused by the modern runtime too).
