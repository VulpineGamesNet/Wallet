package net.vulpinegames.wallet.loot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.vulpinegames.wallet.Wallet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * How many coins coin loot minted, per UTC day and per rule ("chest:rich",
 * "mob:walker"). Coins are items, so nothing else in the economy records them
 * until (and unless) someone deposits them - this is the tuning signal.
 *
 * Kept in world/wallet-coinloot.json, not the database: no schema change, it
 * survives restarts, and world backups carry it. Saved at most once a minute
 * while something changed, and on server stop. Thread-safe: some mods fill
 * chest loot off the server thread.
 */
public final class CoinLootStats {
	public static final class Entry {
		public long drops;
		public long value;
	}

	private static final int KEEP_DAYS = 120;
	private static final long SAVE_INTERVAL_MS = 60_000;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	/** day (yyyy-MM-dd) -> source -> totals; sorted so the file reads in order. */
	private final TreeMap<String, Map<String, Entry>> days = new TreeMap<>();
	private boolean dirty;
	private long lastSave;

	public CoinLootStats(Path file) {
		this.file = file;
		load();
	}

	public static String today() {
		return LocalDate.now(ZoneOffset.UTC).toString();
	}

	public synchronized void record(String source, int value) {
		Entry e = days.computeIfAbsent(today(), d -> new TreeMap<>()).computeIfAbsent(source, s -> new Entry());
		e.drops++;
		e.value += value;
		dirty = true;
		long now = System.currentTimeMillis();
		if (now - lastSave >= SAVE_INTERVAL_MS) save();
	}

	/** The last n days, newest first, each a copy: source -> totals. */
	public synchronized List<Map.Entry<String, Map<String, Entry>>> lastDays(int n) {
		List<Map.Entry<String, Map<String, Entry>>> out = new ArrayList<>();
		for (String day : days.descendingKeySet()) {
			if (out.size() >= n) break;
			Map<String, Entry> copy = new TreeMap<>();
			days.get(day).forEach((k, v) -> {
				Entry c = new Entry();
				c.drops = v.drops;
				c.value = v.value;
				copy.put(k, c);
			});
			out.add(Map.entry(day, copy));
		}
		return out;
	}

	public synchronized void save() {
		lastSave = System.currentTimeMillis();
		if (!dirty) return;
		while (days.size() > KEEP_DAYS) days.pollFirstEntry();
		try {
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(days));
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			dirty = false;
		} catch (IOException e) {
			Wallet.LOGGER.warn("[Wallet] Could not save {}: {}", file, e.toString());
		}
	}

	private void load() {
		if (!Files.exists(file)) return;
		try {
			Map<String, Map<String, Entry>> read = GSON.fromJson(Files.readString(file),
				new TypeToken<LinkedHashMap<String, LinkedHashMap<String, Entry>>>() {}.getType());
			if (read != null) read.forEach((day, sources) -> days.put(day, new TreeMap<>(sources)));
		} catch (IOException | RuntimeException e) {
			// Stats only - start fresh, but keep the unreadable file for a human.
			Wallet.LOGGER.warn("[Wallet] Could not read {} ({}), starting coin loot stats fresh", file, e.toString());
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// The next save overwrites it; nothing else to do.
			}
		}
	}
}
