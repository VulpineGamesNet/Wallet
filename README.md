# Wallet

Server-side economy mod for the VulpineGames servers: wallets, physical coin
items, sign shops, purchasable plots and transaction history, backed by MySQL.
A Java port of the `kubeshop.js` KubeJS scripts that run on ATM10 and
DeceasedCraft - one codebase instead of two diverged scripts.

**Server-side only.** Clients never need the mod installed. Coin textures come
from either side: the jar embeds them (a mod jar is also a resource pack, its
`assets/minecraft` model overrides apply), so a client that installs the mod
needs no resource pack - and clients without the mod keep using the optional
server resource pack, unchanged (same CustomModelData values, same file paths,
so running both causes no conflict).

## Targets

| Jar | MC | Loader | Server |
|---|---|---|---|
| `wallet-*-forge+1.20.1` | 1.20.1 | Forge 47.x | DeceasedCraft |
| `wallet-*-neoforge+1.21.1` | 1.21.1 | NeoForge 21.1.x | ATM10 |
| `wallet-*-fabric+1.20.1 / +1.21.1` | 1.20.1 / 1.21.1 | Fabric | build-only |

Built with [Stonecutter](https://stonecutter.kikugie.dev/) (version axis) and
per-loader source scopes in one tree. HikariCP and the MariaDB JDBC driver ship
jar-in-jar.

## Building

```sh
./gradlew ':1.21.1-neoforge:build' ':1.20.1-forge:build'   # or all four
```

Jars land in `versions/<target>/build/libs/`. Before committing, run
`./gradlew "Reset active project"`.

## Config

**Zero config needed.** Without a config file the mod stores everything in an
SQLite database at `<world>/wallet.db` - drop the jar in `mods/` and the
economy works. MySQL is the opt-in for networks that want an external or
shared database:

`config/kubeshop.json` (falls back to the legacy `kubejs/config/kubeshop.json`
so the scripts' config keeps working):

```json
{
  "backend": "mysql",
  "host": "localhost",
  "port": 3306,
  "database": "minecraft",
  "user": "root",
  "password": "",
  "strictItemMatch": true,
  "walletTop": false,
  "itemChatLinks": false
}
```

`backend`: `"sqlite"` (default) or `"mysql"`. A legacy config with MySQL
fields but no `backend` key is treated as `"mysql"`, so the two prod servers
keep hitting their existing databases untouched. `"sqliteFile"` overrides the
SQLite location (relative paths resolve against the server directory).

`strictItemMatch`: component/NBT-exact stack matching for shops (ATM10
behavior). DeceasedCraft sets `false` - its shops always matched by item id
and its DB templates were written under that rule.

`walletTop`: enables `/wallet top` (top-10 balance leaderboard, server wallet
excluded). Off by default; the command is hidden while disabled. Flipping it
takes a `/reload` (or restart) to show up in tab completion.

`itemChatLinks`: item names in chat as client-mod link tokens (ATM10's pack
ships that client mod - set `true` there). Default off = vanilla hover text,
correct for DeceasedCraft and everyone else.

Per-server deploy configs, explicitly:
- ATM10: `{"backend": "mysql", ..., "itemChatLinks": true}`
- DeceasedCraft: `{"backend": "mysql", ..., "strictItemMatch": false}` - without
  the key its id-only shops silently flip to exact matching and stop trading.

`/wallet admin reload` also revives a database that was down at server start
(the admin subtree stays visible to OPs while the economy is offline).

Tables: `wallet_balances`, `wallet_shops`, `wallet_history` (+
`wallet_schema` for migrations). Migrating a script-era database
(`kubeshop_*` prefix) is a ONE-OFF operational step for the two prod servers,
deliberately not mod logic: run `scripts/migrate-kubeshop-to-wallet.sql`
against the DB **before the mod's first boot there** (boot-first creates
empty `wallet_*` tables and the renames then refuse to clobber them). The
same file carries the rollback statements - rolling back to the KubeJS
scripts means renaming the tables back first. **ATM10's `archive_event.js` writes the wallet table directly -
update it to `wallet_balances` in the same restart that deploys the mod.**
`/wallet admin reload` re-reads config and caches after a hand-edited DB row.

## Schema migrations

Versioned and automatic: `wallet_schema` records applied versions, pending
steps run once, in order, in a transaction, on both backends. A failed step
leaves the version unstamped (retried next boot, economy offline until it
succeeds - loud, never half-applied and silent), and a database newer than
the mod is refused instead of downgraded. Legacy script DBs have no version
table and read as version 0; migration 1 is the idempotent bootstrap, so
adopting them is the same path as a fresh database.

Adding a future change = write `migration2(conn)`, bump `SCHEMA_VERSION`,
add one `if (v < 2)` line in `Database.migrate()`. MySQL auto-commits DDL, so
write DDL steps re-runnable (`IF NOT EXISTS` / guarded ALTER); data-only steps
get real transactions on both backends.

## Features / commands

Same `/wallet` tree as the scripts: balance, pay, withdraw/deposit (commands
and coin right-click), history, sign shops (`[BUY]`/`[SELL]` + price, crouch
right-click to create), server/infinite shop conversion, admin tools.
See the servers' READMEs for the player-facing docs.

**Plots are deliberately NOT in this mod.** They stay a KubeJS feature: the
standalone `plots_command.js` in each server repo grafts `/wallet plot` and
`/wallet admin plot` onto this mod's command tree (Brigadier merges same-name
roots), owns the `kubeshop_plots` table (this mod's migration leaves it
untouched), and pays through `wallet_balances`/`wallet_history` - the same
schema-is-the-API coupling `archive_event.js` has. The claim-mod integrations
(FTB Chunks / OPAC) live in those scripts, so this mod needs no claim mod for
anything: shops are fully self-protected.

Coin world loot ships as an embedded datapack (`neoforge:add_table` global
loot modifiers) - active on the 1.21.1 NeoForge target, inert elsewhere.

## Improvements over the scripts

- Shop clicks are consumed at highest event priority, so claim mods never veto
  them - the FTB protection-bypass tick hack is gone entirely.
- No DB polling: shop keys load once and mutate in memory.
- Hikari pool with 3 s timeouts instead of a raw connection per query.
- Breaking the block a shop sign hangs on is now covered by shop protection.
- Shops are fully self-protected, no claim mod needed: only the owner (or an
  OP) can open a shop chest or break the sign/chest/support block, explosions
  leave shop blocks standing, and a piston that would move or destroy a shop
  sign, chest or support block is refused (server shops: staff only). Double
  chests are protected as one unit (both halves), a shop cannot be created on a
  container that already carries someone else's shop, and while the economy is
  offline a known shop chest stays LOCKED rather than falling open.
  Known gap, deliberate for now: automation theft (hoppers, pipes) - claim
  mods still cover that where installed. Explosion/piston hooks are
  Forge/NeoForge-only (Fabric API has no such events; Fabric is build-only).
- `/reload` re-registers commands (the KubeJS command-tree trap is gone), and
  `/wallet admin reload` refreshes config/caches without a restart.

## Before first deploy (P5 gate)

- Dump `SELECT DISTINCT sign_key FROM kubeshop_shops LIMIT 5` (pre-adoption
  name; and plot_key)
  from BOTH prod DBs and verify `Positions.blockKey`/`playerPlot` reproduce
  the format byte-for-byte.
- Run each server's jar against a **copy** of its prod DB; walk the smoke
  checklist in `atm10/docs/kubeshop-mod-plan.md`.
- Deploy = drop jar in `mods/`, delete `kubeshop.js` (+ `coin_loot.js` and
  `kubejs/data/kubeshop` + `data/neoforge/loot_modifiers` on ATM10 - same
  restart, or coin loot double-injects), restart. DC restarts stay natural.

TODO: no automated tests yet - the P5 staging pass against prod-DB copies is
the acceptance gate for now.
