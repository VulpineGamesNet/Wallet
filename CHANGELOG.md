# Changelog

## 0.1.0-alpha.3

- Coin loot, configured by a `coinLoot` block in `config/wallet.json` (off unless enabled):
  coins rolled into loot chests (loot table id regex, first match wins) and dropped by mobs a
  real player killed (entity type id). Fake players and kills with no recent player hit earn
  nothing; mob coins spawn straight into the world, so drop-clearing mods cannot eat them.
  Wired on Forge 1.20.1: chests through the `wallet:coin_chest` global loot modifier (works
  with Lootr's per-player rolls), mobs through the drops event. ATM10 keeps its datapack.
- `/wallet admin coinloot [days]`: coins minted per UTC day and per rule, kept in
  `<world>/wallet-coinloot.json`.
- DeceasedCraft rule set: `docs/coin-loot-deceasedcraft.json`.

## 0.1.0

- Initial port of the KubeJS wallet scripts to a standalone server-side mod.
- FramedBlocks' framed wall sign (`framedblocks:framed_wall_sign`) works as a shop sign.
