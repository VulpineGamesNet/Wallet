-- ============================================================================
-- ONE-OFF migration for the two production servers (ATM10, DeceasedCraft):
-- KubeJS-script-era kubeshop_* tables -> the Wallet mod's names.
-- The mod deliberately carries NO adoption logic - run this manually, ONCE,
-- BEFORE the mod's first boot on that database. (Booting the mod first
-- creates empty wallet_* tables; these renames then fail, which is the guard
-- against clobbering.)
-- Data, indexes and auto-increment travel with the renames.
--
-- WARNING: anything else writing the old names breaks after this. On ATM10,
-- archive_event.js pays into kubeshop_wallets - update it in the same restart.
-- ============================================================================

ALTER TABLE kubeshop_wallets RENAME TO wallet_balances;
ALTER TABLE kubeshop_shops   RENAME TO wallet_shops;
ALTER TABLE kubeshop_history RENAME TO wallet_history;

-- When deploying the WalletPlots addon (skip while plots_command.js runs -
-- the script still owns kubeshop_plots under its old name):
-- ALTER TABLE kubeshop_plots RENAME TO wallet_plots;

-- Rollback (also required before ever running the KubeJS scripts again):
-- ALTER TABLE wallet_balances RENAME TO kubeshop_wallets;
-- ALTER TABLE wallet_shops    RENAME TO kubeshop_shops;
-- ALTER TABLE wallet_history  RENAME TO kubeshop_history;
-- ALTER TABLE wallet_plots    RENAME TO kubeshop_plots;
-- DROP TABLE IF EXISTS wallet_schema;        -- so a later re-adoption starts clean
-- DROP TABLE IF EXISTS walletplots_schema;
