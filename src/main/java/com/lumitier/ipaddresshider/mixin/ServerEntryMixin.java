package com.lumitier.ipaddresshider.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.lumitier.ipaddresshider.IpAddressHider;
import com.lumitier.ipaddresshider.client.Mask;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Verdeckter Servername in der Mehrspieler-Liste.
 *
 * WIE: render() liest ServerInfo.name genau EINMAL und reicht den Wert an
 * drawTextWithShadow weiter (im Bytecode von 1.21.11 nachgesehen). Genau
 * dieser eine Lesezugriff wird ersetzt - nicht das Zeichnen, nicht der
 * Eintrag, nicht der gespeicherte Name. ModifyExpressionValue ist
 * stapelbar: Eine andere Mod, die denselben Wert anfasst, bekommt das
 * Ergebnis dieser hier und umgekehrt, statt sich gegenseitig zu
 * verdraengen.
 *
 * WAS NICHT ANGEFASST WIRD: MOTD, Ping, Spielerzahl, Icon. Und die
 * Bildschirmvorlesung - die liest den Namen ueber einen anderen Weg und
 * landet nicht im Stream-Bild.
 */
@Mixin(MultiplayerServerListWidget.ServerEntry.class)
public abstract class ServerEntryMixin {

	@Shadow
	@Final
	private ServerInfo server;

	/**
	 * Gewuerfelte Sternchenzahl fuer DIESEN Listeneintrag, -1 = noch nicht.
	 *
	 * Am Eintrag, nicht in einer Tabelle: Refresh baut neue Eintraege,
	 * damit ist neu Wuerfeln bei Refresh genau das, was ohnehin passiert.
	 */
	@Unique
	private int ipaddresshider$stars = -1;

	@ModifyExpressionValue(
		method = "render(Lnet/minecraft/client/gui/DrawContext;IIZF)V",
		at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/network/ServerInfo;name:Ljava/lang/String;"))
	private String ipaddresshider$maskName(String name) {
		if (!IpAddressHider.isNameHidden(this.server.address)) {
			return name;
		}
		if (this.ipaddresshider$stars < 0) {
			this.ipaddresshider$stars = Mask.randomListLength();
		}
		return Mask.stars(this.ipaddresshider$stars);
	}
}
