package com.lumitier.ipaddresshider.client;

import com.lumitier.ipaddresshider.IpAddressHider;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

/**
 * Einstellungen dieser Mod.
 *
 * WARUM KEIN YACL:
 * Diese Mod hat zwei Schalter. YACL waere eine knapp megabytegrosse
 * Abhaengigkeit, die jeder mitinstallieren muesste, der nur seine IP
 * verdecken will - fuer einen Bildschirm, der aus drei Knoepfen
 * besteht. Die anderen Mods der Reihe benutzen YACL, weil sie
 * zwanzig Optionen haben; hier waere es Ballast.
 *
 * Erreichbar ueber Mod Menu. Ohne Mod Menu laesst sich der
 * Streamer-Modus in config/ipaddresshider.json von Hand setzen.
 */
public final class SettingsScreen extends Screen {

	private static final int WIDTH = 220;
	private static final int ROW_H = 20;
	private static final int GAP = 8;

	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Text.translatable("ipaddresshider.settings.title"));
		this.parent = parent;
	}

	/** Weiss, deckend. Textfarben sind ARGB - ohne 0xFF waere es unsichtbar. */
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFA0A0A0;
	private static final int PURPLE = 0xFFB98CFF;

	/** Zuletzt gesehener Twitch-Zustand - aendert er sich, wird neu aufgebaut. */
	private int seenVersion = -1;

	/** Zeilen, die render() zeichnet. y < 0 heisst: keine. */
	private Text statusLine;
	private int statusY = -1;
	private String codeLine;
	private int codeY = -1;

	@Override
	protected void init() {
		this.seenVersion = TwitchLink.version();
		this.statusLine = null;
		this.statusY = -1;
		this.codeLine = null;
		this.codeY = -1;

		int x = this.width / 2 - WIDTH / 2;
		int y = this.height / 4 + 24;

		// Zeigt NUR den von Hand gesetzten Schalter. Dass Twitch den Modus
		// gerade zusaetzlich einschaltet, steht darunter in der Statuszeile
		// - sonst wuerde dieser Knopf "an" zeigen und beim Klick scheinbar
		// nichts tun.
		addDrawableChild(CyclingButtonWidget.onOffBuilder(
				IpAddressHider.manualStreamerMode())
			.tooltip(value -> Tooltip.of(
				Text.translatable("ipaddresshider.option.streamer_mode.desc")))
			.build(x, y, WIDTH, ROW_H,
				Text.translatable("ipaddresshider.option.streamer_mode"),
				(button, value) -> IpAddressHider.setStreamerMode(value)));

		y += ROW_H + GAP;

		// Nur sinnvoll mit verbundenem Twitch - ohne ist er ausgegraut, und
		// der Tooltip sagt warum.
		boolean connected = TwitchLink.state() == TwitchLink.State.CONNECTED;
		CyclingButtonWidget<Boolean> onlyLive = CyclingButtonWidget.onOffBuilder(
				IpAddressHider.streamerOnlyWhileLive())
			.tooltip(value -> Tooltip.of(Text.translatable(connected
				? "ipaddresshider.option.only_live.desc"
				: "ipaddresshider.option.only_live.needs_twitch")))
			.build(x, y, WIDTH, ROW_H,
				Text.translatable("ipaddresshider.option.only_live"),
				(button, value) -> IpAddressHider.setStreamerOnlyWhileLive(value));
		onlyLive.active = connected;
		addDrawableChild(onlyLive);

		y += ROW_H + GAP;

		switch (TwitchLink.state()) {
			case DISCONNECTED, FAILED -> {
				if (TwitchLink.state() == TwitchLink.State.FAILED) {
					this.statusLine = Text.translatable("ipaddresshider.twitch.failed");
					this.statusY = y;
					y += 12;
				}
				addDrawableChild(ButtonWidget.builder(
						Text.translatable("ipaddresshider.option.twitch"),
						b -> TwitchLink.connect())
					.dimensions(x, y, WIDTH, ROW_H)
					.tooltip(Tooltip.of(Text.translatable("ipaddresshider.option.twitch.desc")))
					.build());
				y += ROW_H;
			}
			case REQUESTING -> {
				ButtonWidget wait = ButtonWidget.builder(
						Text.translatable("ipaddresshider.twitch.requesting"), b -> {})
					.dimensions(x, y, WIDTH, ROW_H).build();
				wait.active = false;
				addDrawableChild(wait);
				y += ROW_H;
			}
			case WAITING_FOR_USER -> {
				this.statusLine = Text.translatable("ipaddresshider.twitch.enter_code");
				this.statusY = y;
				y += 12;
				this.codeLine = TwitchLink.userCode();
				this.codeY = y;
				y += 14;
				int half = (WIDTH - GAP) / 2;
				addDrawableChild(ButtonWidget.builder(
						Text.translatable("ipaddresshider.twitch.open"),
						b -> net.minecraft.util.Util.getOperatingSystem()
							.open("https://www.twitch.tv/activate"))
					.dimensions(x, y, half, ROW_H).build());
				addDrawableChild(ButtonWidget.builder(
						Text.translatable("ipaddresshider.twitch.copy"),
						b -> {
							String code = TwitchLink.userCode();
							if (code != null && this.client != null) {
								this.client.keyboard.setClipboard(code);
							}
						})
					.dimensions(x + half + GAP, y, half, ROW_H).build());
				y += ROW_H + GAP;
				addDrawableChild(ButtonWidget.builder(
						net.minecraft.screen.ScreenTexts.CANCEL,
						b -> TwitchLink.disconnect())
					.dimensions(x, y, WIDTH, ROW_H).build());
				y += ROW_H;
			}
			case CONNECTED -> {
				this.statusLine = Text.translatable(TwitchLink.live()
						? "ipaddresshider.twitch.connected_live"
						: "ipaddresshider.twitch.connected_offline",
					TwitchLink.login() == null ? "?" : TwitchLink.login());
				this.statusY = y;
				y += 12;
				addDrawableChild(ButtonWidget.builder(
						Text.translatable("ipaddresshider.twitch.disconnect"),
						b -> TwitchLink.disconnect())
					.dimensions(x, y, WIDTH, ROW_H).build());
				y += ROW_H;
			}
		}

		y += GAP * 2;

		addDrawableChild(ButtonWidget.builder(net.minecraft.screen.ScreenTexts.DONE,
				b -> close())
			.dimensions(x, y, WIDTH, ROW_H)
			.build());
	}

	/**
	 * Die Anmeldung laeuft im Hintergrund. Aendert sich dort etwas - Code
	 * da, verbunden, live -, wird der Bildschirm neu aufgebaut. Abgefragt
	 * wird eine Zahl pro Tick, sonst nichts.
	 */
	@Override
	public void tick() {
		super.tick();
		if (TwitchLink.version() != this.seenVersion) {
			clearAndInit();
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int cx = this.width / 2;
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title,
			cx, this.height / 4, WHITE);
		if (this.statusLine != null && this.statusY >= 0) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, this.statusLine,
				cx, this.statusY, GREY);
		}
		if (this.codeLine != null && this.codeY >= 0) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(this.codeLine),
				cx, this.codeY, PURPLE);
		}
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}
}
