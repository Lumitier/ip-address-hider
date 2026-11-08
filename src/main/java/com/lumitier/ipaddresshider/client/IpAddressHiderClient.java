package com.lumitier.ipaddresshider.client;

import com.lumitier.ipaddresshider.IpAddressHider;
import net.fabricmc.api.ClientModInitializer;

/**
 * Einstieg auf Client-Seite.
 *
 * Tut genau eine Sache: die Liste der verdeckten Adressen laden. Alles
 * Weitere passiert im Mixin, wenn der Bildschirm zum Bearbeiten eines
 * Servers aufgeht.
 */
public final class IpAddressHiderClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		IpAddressHider.load();
		// Laedt einen gespeicherten Twitch-Login, falls vorhanden. Ohne
		// Login passiert hier nichts und es geht keine Anfrage raus.
		TwitchLink.start();
		// Eintrag im LumiLib-Hub - nur wenn LumiLib installiert ist. Der
		// Verweis steckt in einer eigenen Klasse, damit LumiLibs Klassen
		// ohne LumiLib nie geladen werden.
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("lumilib")) {
			try {
				LumiLibCompat.register();
			} catch (Throwable t) {
				IpAddressHider.LOGGER.warn("[IP Address Hider] Could not register with LumiLib.", t);
			}
		}
		IpAddressHider.LOGGER.info("[IP Address Hider] Client init done. "
			+ IpAddressHider.cfg.hidden.size() + " hidden address(es).");
	}
}
