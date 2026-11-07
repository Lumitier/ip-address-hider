package com.lumitier.ipaddresshider.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Zahnrad in der Mod-Liste.
 *
 * Diese Klasse wird NUR geladen, wenn Mod Menu installiert ist - der
 * Einstiegspunkt dafuer steht in der fabric.mod.json und wird von Mod
 * Menu selbst aufgerufen. Fehlt Mod Menu, fasst niemand diese Klasse
 * an, und ihre Importe stoeren nicht. Deshalb steht Mod Menu auch
 * unter "suggests" und nicht unter "depends".
 */
public final class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SettingsScreen::new;
	}
}
