# WalletPlots — addon plan

Plots return as a separate mod, `walletplots`, hard-depending on `wallet`.
Feature scope = exactly the existing plots script (chunk plots, same commands,
same messages). No box plots, no protection of its own, no new config — parity
first, ideas later.

## Shape

| | |
|---|---|
| Repo | `~/Projects/WalletPlots`, scaffold copied from Wallet (same Stonecutter matrix, same 4 targets) |
| Mod id | `walletplots`, metadata `depends: wallet` on every loader |
| Commands | registers its own `literal("wallet") → plot` and `wallet → admin → plot` trees; Brigadier merges same-name roots, players see one `/wallet` |
| Storage | **Wallet's pool** — same SQLite file or MySQL database, automatically. Plots on SQLite is new (the script was MySQL-only), which also unblocks local-instance testing without the MariaDB container |
| Table | owns `wallet_plots`; migration 1 is a clean bootstrap only. Adopting the prod servers' `kubeshop_plots` is the ONE-OFF `scripts/migrate-kubeshop-to-wallet.sql` (plots rename included there), run manually before the addon's first boot - no adoption logic in the addon |
| Versioning | own `walletplots_schema` table, copy of Wallet's ~60-line runner (not shared code — addons must not break when core's runner evolves) |
| Claims | FTB Chunks / OPAC reflection providers, runtime-optional, exactly the deleted `ClaimProvider`/`FtbClaims`/`OpacClaims` classes — restore from git blobs (they were staged before deletion; `git fsck --lost-found` in the Wallet repo) or rewrite from `plots_command.js` |

## Wallet-side prerequisite: `WalletApi`

One small stable facade in core, `net.vulpinegames.wallet.api.WalletApi` —
addons never touch DAOs or internals:

```java
boolean isAvailable();
boolean isMysql();
Connection connection() throws SQLException;   // the shared pool, for addon-owned tables
int getBalance(String uuid);
boolean addBalance(String uuid, int amount);
boolean removeBalance(String uuid, int amount);  // guarded, false = insufficient/failed
void addHistory(String uuid, String type, int amount, String other, String desc);
String playerName(MinecraftServer server, String uuid);
```

`plot_buy`/`plot_sell` history rows keep rendering in `/wallet history` — core
already kept those cases. Second customer later: an archive addon replacing
archive_event.js's raw table writes.

## Lifecycle

Cross-mod ServerStarted ordering is not guaranteed. Addon tries to bootstrap
(migrations, cache) in its ServerStarted handler; if `Wallet.active()` is not
ready yet, it defers and lazily bootstraps on first plot command. No busy
waiting, no core-side hooks.

## Command tree (parity, verbatim from the script)

- `/wallet plot` — info + clickable [BUY]
- `/wallet plot buy` — race-guarded take/transfer, claim-state check first,
  checked debit with rollback, claim-for-buyer, seller paid + notified
- `/wallet plot sell <price>` / `unsell` / `list`
- `/wallet admin plot add <price> [name]` / `rename <name>` /
  `claim_as <player>` (offline allowed only when the provider supports it —
  OPAC yes, FTB no) / `reclaim` / `remove` / `list` (with [TP] links)

## Build wiring (the P0 risk, like shadow was for Wallet)

Addon compiles against Wallet's classes per target. Plan A: Wallet
`publishToMavenLocal` per target (build-logic already carries publishing),
addon consumes `net.vulpinegames:wallet-<target>` as `compileOnly` +
`modImplementation`/run-time mod. Plan B fallback: direct file dependency on
`../Wallet/versions/<target>/build/libs/wallet-*.jar`. P0 acceptance: all 4
addon jars build AND a dev `runServer` boots with both mods and `/wallet plot`
answers.

## Phases

| Phase | Deliverable | Acceptance |
|---|---|---|
| P0 | scaffold + dependency wiring | 4 jars, dev server boots with both mods, stub `/wallet plot` answers |
| P1 | `WalletApi` in core + addon DB layer + migration (kubeshop_plots adoption) | plots table adopted/created on both backends; SQLite runnable check like core's |
| P2 | claims providers + full command tree | parity checklist vs plots_command.js on the test instance (FTB in pack); claim_as offline refused on FTB |
| P3 | deploy prep | plots_command.js retired from both repos' deploy plan; README/deploy docs updated on all three repos |

## Retirement of plots_command.js

The scripts in atm10/decesedcraft repos become dead the release WalletPlots
ships — never run both (double `/wallet plot`). Until then they remain the
deploy-day plan.
