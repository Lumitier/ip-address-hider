package com.lumitier.ipaddresshider.client;

import com.lumitier.ipaddresshider.IpAddressHider;
import com.lumitier.lumilib.api.LumiLibApi;
import com.lumitier.lumilib.api.LumiMod;
import com.lumitier.lumilib.api.SettingsPage;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

/**
 * Anmeldung bei LumiLib - dieselben Einstellungen wie im eigenen
 * Bildschirm, nur direkt im Hub.
 *
 * EIGENE KLASSE, weil LumiLib OPTIONAL ist: Erst wenn jemand diese Klasse
 * anfasst, werden LumiLibs Klassen aufgeloest. IpAddressHiderClient fragt
 * vorher, ob LumiLib da ist. Ohne LumiLib bleibt alles wie in 1.0.0 - der
 * eigene Bildschirm ueber Mod Menu.
 *
 * KEINE SERVER-STEUERUNG. Die anderen Mods der Reihe hoeren hier auf
 * LumiLibControlPlugin und lassen sich vom Server abschalten. Diese nicht:
 * Sie schuetzt die Privatsphaere des Spielers, und ein Server, der sie
 * abschalten kann, koennte genau das aushebeln.
 *
 * DIE SEITE WIRD NEU GEBAUT, sobald sich die Twitch-Anmeldung aendert
 * (refreshWhen): "Verbinden" wird zu "Code eingeben", dann zu "Verbunden
 * als ...". Schalter lesen ihren Wert ohnehin in jedem Bild.
 */
public final class LumiLibCompat {

	private LumiLibCompat() {}

	public static void register() {
		ModContainer container = FabricLoader.getInstance()
			.getModContainer(IpAddressHider.MOD_ID).orElse(null);
		String version = container == null ? ""
			: container.getMetadata().getVersion().getFriendlyString();
		String name = container == null ? "IP Address Hider"
			: container.getMetadata().getName();
		String summary = container == null ? ""
			: container.getMetadata().getDescription();

		LumiLibApi.register(new LumiMod(
			IpAddressHider.MOD_ID,
			Text.literal(name),
			version,
			Text.literal(summary),
			// Icon liest LumiLib aus dem Jar.
			null,
			// Der eigene Bildschirm bleibt als Rueckfallebene.
			SettingsScreen::new,
			LumiLibCompat::settings));
	}

	private static SettingsPage settings() {
		SettingsPage.Builder b = SettingsPage.builder(IpAddressHider.MOD_ID)
			.refreshWhen(TwitchLink::version);

		boolean connected = TwitchLink.state() == TwitchLink.State.CONNECTED;

		// --- Streamer-Modus ---------------------------------------------
		b.section("streamer");
		// Nur der von Hand gesetzte Schalter - wie im eigenen Bildschirm.
		b.toggle("streamer_mode",
			IpAddressHider::manualStreamerMode,
			IpAddressHider::setStreamerMode);
		if (connected) {
			b.toggle("only_live",
				IpAddressHider::streamerOnlyWhileLive,
				IpAddressHider::setStreamerOnlyWhileLive);
		} else {
			// Ohne Twitch waere der Schalter wirkungslos. Statt ihn
			// auszugrauen - das kann der Hub nicht - steht da, warum.
			b.row(new SettingsPage.Note(
				Text.translatable("ipaddresshider.option.only_live"),
				Text.translatable("ipaddresshider.option.only_live.needs_twitch"),
				false));
		}

		// --- Twitch -------------------------------------------------------
		b.section("twitch");
		switch (TwitchLink.state()) {
			case DISCONNECTED, FAILED -> {
				// "Fehlgeschlagen" NUR nach einem echten Versuch. Ohne Versuch,
				// oder nach einem abgelaufenen Login, steht schlicht da, dass
				// nicht verbunden ist.
				b.row(TwitchLink.state() == TwitchLink.State.FAILED
					? new SettingsPage.Note(Text.translatable("ipaddresshider.twitch.failed"), null, true)
					: new SettingsPage.Note(Text.translatable("ipaddresshider.twitch.not_connected"), null, false));
				b.action("twitch", TwitchLink::connect);
			}
			case REQUESTING -> b.row(new SettingsPage.Note(
				Text.translatable("ipaddresshider.twitch.requesting"), null, false));
			case WAITING_FOR_USER -> {
				String code = TwitchLink.userCode();
				b.row(new SettingsPage.Note(
					Text.literal(code == null ? "..." : code),
					Text.translatable("ipaddresshider.twitch.enter_code"),
					false));
				b.row(new SettingsPage.Action(
					Text.translatable("ipaddresshider.twitch.open"), null,
					() -> Util.getOperatingSystem().open("https://www.twitch.tv/activate")));
				b.row(new SettingsPage.Action(
					Text.translatable("ipaddresshider.twitch.copy"), null,
					() -> {
						String c = TwitchLink.userCode();
						if (c != null) {
							MinecraftClient.getInstance().keyboard.setClipboard(c);
						}
					}));
				b.row(new SettingsPage.Action(ScreenTexts.CANCEL, null, TwitchLink::disconnect));
			}
			case CONNECTED -> {
				b.row(new SettingsPage.Note(
					Text.translatable(TwitchLink.live()
							? "ipaddresshider.twitch.connected_live"
							: "ipaddresshider.twitch.connected_offline",
						TwitchLink.login() == null ? "?" : TwitchLink.login()),
					null, false));
				b.row(new SettingsPage.Action(
					Text.translatable("ipaddresshider.twitch.disconnect"), null,
					TwitchLink::disconnect));
			}
		}
		return b.build();
	}
}
