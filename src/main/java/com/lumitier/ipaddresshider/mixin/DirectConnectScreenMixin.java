package com.lumitier.ipaddresshider.mixin;

import com.lumitier.ipaddresshider.IpAddressHider;
import com.lumitier.ipaddresshider.client.AddressLock;
import com.lumitier.ipaddresshider.client.Mask;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dasselbe Schloss in der Direktverbindung.
 *
 * MIT ZWEI UNTERSCHIEDEN:
 *
 * Erstens wird keine ADRESSE gespeichert. Die Direktverbindung hat
 * keinen Servereintrag, an dem ein Haken haengen koennte. Gemerkt wird
 * nur, ob dieses Feld verdeckt sein soll - ein Schalter, keine Liste.
 * Ohne den waere das Schloss wirkungslos: Minecraft merkt sich die
 * zuletzt eingegebene Adresse von selbst, und sie stuende beim naechsten
 * Oeffnen wieder offen da.
 *
 * Zweitens verriegelt der Streamer-Modus dieses Feld genauso wie das
 * eines gespeicherten Servers: zu, abgeblendet, nicht aufsperrbar.
 *
 * DAS HAT EINEN PREIS, und der ist bewusst in Kauf genommen: Ein
 * verdecktes Feld ist gesperrt, man kann im Streamer-Modus hier also
 * keine neue Adresse eintippen. Wer das muss, schaltet den Modus kurz
 * aus. Die Alternative waere ein Schloss, das sich ausgerechnet im
 * Streamer-Modus oeffnen laesst - und damit kein Schloss.
 *
 * Und eine Selbstverstaendlichkeit, die trotzdem verwirrt: Ist das Feld
 * leer, zeichnet Minecraft gar keinen Text - dann stehen da auch keine
 * Sternchen. Es gibt schlicht nichts zu verdecken.
 */
@Mixin(DirectConnectScreen.class)
public abstract class DirectConnectScreenMixin extends Screen {

	@Shadow
	private TextFieldWidget addressField;

	protected DirectConnectScreenMixin(Text title) {
		super(title);
	}

	@Unique
	private boolean ipaddresshider$known;

	@Unique
	private boolean ipaddresshider$marked;

	@Unique
	private int ipaddresshider$stars;

	/**
	 * Text, der beim ERSTEN Oeffnen im Feld stand.
	 *
	 * Lebt hier, weil er jeden Neuaufbau ueberstehen muss - siehe
	 * AddressLock.initialText.
	 */
	@Unique
	private String ipaddresshider$initialText = "";

	/** Schon getippt? Ueberlebt jeden Neuaufbau, wird nie zurueckgesetzt. */
	@Unique
	private boolean ipaddresshider$typed;

	@Inject(method = "init()V", at = @At("TAIL"))
	private void ipaddresshider$addLockButton(CallbackInfo ci) {
		TextFieldWidget field = this.addressField;
		if (field == null) {
			return;
		}
		if (!this.ipaddresshider$known) {
			this.ipaddresshider$stars = Mask.randomLength();
			this.ipaddresshider$initialText = field.getText();
			// Der selbst gesetzte Zustand. Den Streamer-Modus rechnet
			// AddressLock dazu - hier steht nur, was der Nutzer wollte.
			this.ipaddresshider$marked = IpAddressHider.directConnectHidden();
			this.ipaddresshider$known = true;
		}
		AddressLock lock = AddressLock.install(field, this.textRenderer,
			AddressLock.ADDRESS_HIDE, AddressLock.ADDRESS_SHOW,
			true, this.ipaddresshider$marked, this.ipaddresshider$stars,
			this.ipaddresshider$initialText,
			() -> this.ipaddresshider$typed,
			() -> this.ipaddresshider$typed = true,
			marked -> {
				this.ipaddresshider$marked = marked;
				IpAddressHider.setDirectConnectHidden(marked);
			});
		this.addDrawableChild(lock.button());
	}
}
