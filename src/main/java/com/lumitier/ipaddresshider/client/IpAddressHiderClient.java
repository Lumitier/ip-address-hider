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
		IpAddressHider.LOGGER.info("[IP Address Hider] Client init done. "
			+ IpAddressHider.cfg.hidden.size() + " hidden address(es).");
	}
}
