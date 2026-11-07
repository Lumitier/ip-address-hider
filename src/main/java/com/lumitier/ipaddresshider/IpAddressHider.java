package com.lumitier.ipaddresshider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Zustand und Config.
 *
 * WAS HIER GESPEICHERT WIRD: eine Liste von Serveradressen, die der Nutzer
 * verdeckt hat. Mehr nicht. Die Datei liegt in config/ipaddresshider.json
 * und verlaesst den Rechner nie - diese Mod macht keine Netzwerkaufrufe.
 *
 * DIE ADRESSE IST DER SCHLUESSEL, nicht der Servername. Der Name ist frei
 * waehlbar und mehrfach vergeben; die Adresse ist das, was versteckt werden
 * soll, und damit das Einzige, was den Eintrag eindeutig meint. Benennt
 * jemand seinen Server um, bleibt die Adresse verdeckt - genau richtig.
 * Aendert er die Adresse, ist es ein anderer Server, und der ist sichtbar,
 * bis man ihn selbst wieder verdeckt.
 */
public final class IpAddressHider {

	public static final String MOD_ID = "ipaddresshider";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private IpAddressHider() {}

	/**
	 * Oeffentliche Felder, direkt von GSON gelesen und geschrieben.
	 *
	 * ACHTUNG: GSON ruft keinen Konstruktor auf, wenn die Datei kaputt ist
	 * oder ein Feld fehlt - deshalb prueft load() hinterher auf null. Ohne
	 * das waere eine von Hand editierte Datei ein Absturz in jedem Frame.
	 */
	public static final class Config {
		public List<String> hidden = new ArrayList<>();

		/**
		 * Server, deren NAME verdeckt ist - eigene Liste, eigenes Schloss.
		 *
		 * SCHLUESSEL IST DIE ADRESSE, NICHT DER NAME. Namen sind frei
		 * waehlbar und koennen doppelt vorkommen ("SMP"), die Adresse
		 * meint den Server eindeutig. Gespeichert wird beim Fertig-Klick
		 * unter der Adresse, die dann im Feld steht.
		 */
		public List<String> hiddenNames = new ArrayList<>();

		/**
		 * Streamer-Modus: verdeckt JEDE gespeicherte Adresse, egal was
		 * einzeln eingestellt ist.
		 *
		 * Bewusst ein eigener Schalter und kein Setzen aller Eintraege:
		 * Beim Ausschalten sollen genau die Server wieder sichtbar sein,
		 * die vorher sichtbar waren. Wuerde der Modus die Liste
		 * beschreiben, waere diese Information weg.
		 */
		public boolean streamerMode = false;

		/**
		 * Streamer-Modus nur wirken lassen, solange der Twitch-Kanal live ist.
		 *
		 * Ein ZWEITER Schalter statt einer dritten Stellung am ersten: Der
		 * Hauptschalter sagt "ich will geschuetzt sein", dieser hier sagt
		 * "aber nur, waehrend ich wirklich sende". Ohne verbundenes Twitch
		 * wird er ignoriert - sonst schaltete man ihn ein und der Modus
		 * taete nie etwas.
		 */
		public boolean streamerOnlyWhileLive = false;

		/**
		 * Schloss der Direktverbindung.
		 *
		 * EIN SCHALTER, KEINE ADRESSE. Die Direktverbindung hat keinen
		 * Servereintrag, an dem ein Haken haengen koennte - und die
		 * gerade erst getippte Adresse dauerhaft in die Liste zu
		 * schreiben waere falsch. Gemerkt wird deshalb nur, ob dieses
		 * eine Feld verdeckt sein soll.
		 *
		 * Ohne das waere der Schalter sinnlos: Minecraft merkt sich die
		 * zuletzt eingegebene Adresse von selbst, das Schloss aber
		 * nicht - man verdeckt sie, drueckt Abbrechen, kommt wieder und
		 * die Adresse steht offen da.
		 */
		public boolean directConnectHidden = false;
	}

	public static volatile Config cfg = new Config();

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
	}

	public static void load() {
		Path p = file();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				Config read = GSON.fromJson(r, Config.class);
				if (read != null) {
					cfg = read;
				}
			} catch (Exception e) {
				LOGGER.warn("[IP Address Hider] Could not read config, starting empty.", e);
				cfg = new Config();
			}
		}
		if (cfg.hiddenNames == null) {
			cfg.hiddenNames = new ArrayList<>();
		}
		if (cfg.hidden == null) {
			cfg.hidden = new ArrayList<>();
		}
		save();
	}

	public static void save() {
		Path p = file();
		try {
			Files.createDirectories(p.getParent());
			try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
				GSON.toJson(cfg, w);
			}
		} catch (Exception e) {
			LOGGER.warn("[IP Address Hider] Could not write config.", e);
		}
	}

	// ------------------------------------------------------------------
	// Abfrage und Umschalten
	//
	// Adressen werden klein geschrieben verglichen. "MC.Hypixel.NET" und
	// "mc.hypixel.net" sind derselbe Server, und wer die Schreibweise im
	// Feld aendert, soll nicht ploetzlich eine sichtbare Adresse haben.
	// ------------------------------------------------------------------

	private static String key(String address) {
		return address == null ? "" : address.trim().toLowerCase(java.util.Locale.ROOT);
	}

	/** Ist diese Adresse EINZELN verdeckt? Ohne Streamer-Modus. */
	public static boolean isMarked(String address) {
		String k = key(address);
		return !k.isEmpty() && cfg.hidden.contains(k);
	}

	/** Soll diese Adresse verdeckt werden - Streamer-Modus eingerechnet? */
	public static boolean isHidden(String address) {
		return streamerMode() || isMarked(address);
	}

	/** Ist der NAME dieses Servers einzeln verdeckt? Ohne Streamer-Modus. */
	public static boolean isNameMarked(String address) {
		String k = key(address);
		return !k.isEmpty() && cfg.hiddenNames.contains(k);
	}

	/**
	 * Soll der Name verdeckt werden - Streamer-Modus eingerechnet?
	 *
	 * Der Streamer-Modus verdeckt auch Namen: Ein Servername wie
	 * "woblasmp" verraet oft genauso viel wie die Adresse dahinter.
	 */
	public static boolean isNameHidden(String address) {
		return streamerMode() || isNameMarked(address);
	}

	public static void setNameHidden(String address, boolean hidden) {
		String k = key(address);
		if (k.isEmpty()) {
			return;
		}
		boolean changed = hidden ? (!cfg.hiddenNames.contains(k) && cfg.hiddenNames.add(k))
		                         : cfg.hiddenNames.remove(k);
		if (changed) {
			save();
		}
	}

	/**
	 * Wirkt der Streamer-Modus gerade?
	 *
	 * Hauptschalter aus: nie. Hauptschalter an: immer - ausser "Nur waehrend
	 * Live" ist an UND Twitch verbunden, dann nur, solange der Kanal live ist.
	 * Twitch schaltet nichts von selbst ein, wenn der Hauptschalter aus ist.
	 */
	public static boolean streamerMode() {
		if (!cfg.streamerMode) {
			return false;
		}
		if (cfg.streamerOnlyWhileLive && com.lumitier.ipaddresshider.client.TwitchLink.state()
				== com.lumitier.ipaddresshider.client.TwitchLink.State.CONNECTED) {
			return com.lumitier.ipaddresshider.client.TwitchLink.live();
		}
		return true;
	}

	public static boolean streamerOnlyWhileLive() {
		return cfg.streamerOnlyWhileLive;
	}

	public static void setStreamerOnlyWhileLive(boolean on) {
		if (cfg.streamerOnlyWhileLive != on) {
			cfg.streamerOnlyWhileLive = on;
			save();
		}
	}

	/** Nur der von Hand gesetzte Schalter - fuer den Einstellungsbildschirm. */
	public static boolean manualStreamerMode() {
		return cfg.streamerMode;
	}

	public static boolean directConnectHidden() {
		return cfg.directConnectHidden;
	}

	public static void setDirectConnectHidden(boolean hidden) {
		if (cfg.directConnectHidden != hidden) {
			cfg.directConnectHidden = hidden;
			save();
		}
	}

	public static void setStreamerMode(boolean on) {
		if (cfg.streamerMode != on) {
			cfg.streamerMode = on;
			save();
		}
	}

	/** @return der neue Zustand */
	public static boolean toggle(String address) {
		String k = key(address);
		if (k.isEmpty()) {
			return false;
		}
		boolean now;
		if (cfg.hidden.remove(k)) {
			now = false;
		} else {
			cfg.hidden.add(k);
			now = true;
		}
		save();
		return now;
	}

	public static void setHidden(String address, boolean hidden) {
		String k = key(address);
		if (k.isEmpty()) {
			return;
		}
		boolean changed = hidden ? (!cfg.hidden.contains(k) && cfg.hidden.add(k))
		                         : cfg.hidden.remove(k);
		if (changed) {
			save();
		}
	}
}
