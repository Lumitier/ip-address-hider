package com.lumitier.ipaddresshider.mixin;

import com.lumitier.ipaddresshider.IpAddressHider;
import com.lumitier.ipaddresshider.client.AddressLock;
import com.lumitier.ipaddresshider.client.LockState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zwei Schloesser im Server-bearbeiten-Bildschirm: eins fuer den Namen,
 * eins fuer die Adresse. Unabhaengig voneinander - der typische Fall ist
 * eine geheime Adresse hinter einem harmlosen Namen.
 *
 * WARUM DER ZUSTAND IM BILDSCHIRM LEBT: siehe LockState. Kurz: init()
 * laeuft bei jeder Fenstergroessenaenderung erneut, und alles, was das
 * ueberstehen muss, darf nicht im Schloss selbst liegen.
 */
@Mixin(AddServerScreen.class)
public abstract class AddServerScreenMixin extends Screen {

	@Shadow
	private TextFieldWidget addressField;

	@Shadow
	private TextFieldWidget serverNameField;

	/** Nie aufgerufen - Mixin verlangt nur einen passenden Konstruktor. */
	protected AddServerScreenMixin(Text title) {
		super(title);
	}

	@Unique
	private final LockState ipaddresshider$address = new LockState();

	@Unique
	private final LockState ipaddresshider$name = new LockState();

	/**
	 * Erst beim Bestaetigen wird gespeichert - beide Schloesser, unter der
	 * Adresse, die dann im Feld steht.
	 *
	 * Auch das Namensschloss haengt an der ADRESSE: Namen sind frei
	 * waehlbar und koennen doppelt vorkommen, die Adresse meint den Server
	 * eindeutig. Abbrechen speichert nichts, wie alles andere auf diesem
	 * Bildschirm auch.
	 */
	@Inject(method = "addAndClose()V", at = @At("HEAD"))
	private void ipaddresshider$saveLocks(CallbackInfo ci) {
		if (this.addressField == null) {
			return;
		}
		String address = this.addressField.getText();
		IpAddressHider.setHidden(address, this.ipaddresshider$address.marked);
		IpAddressHider.setNameHidden(address, this.ipaddresshider$name.marked);
	}

	@Inject(method = "init()V", at = @At("TAIL"))
	private void ipaddresshider$addLockButtons(CallbackInfo ci) {
		if (this.addressField == null || this.serverNameField == null) {
			return;
		}
		// Beim ersten Oeffnen kommt der Zustand aus der Config. Fuer den
		// Namen wird mit der ADRESSE nachgeschlagen, siehe oben.
		String address = this.addressField.getText();
		this.ipaddresshider$address.firstOpen(address, IpAddressHider.isMarked(address));
		this.ipaddresshider$name.firstOpen(this.serverNameField.getText(),
			IpAddressHider.isNameMarked(address));

		ipaddresshider$install(this.serverNameField, this.ipaddresshider$name,
			AddressLock.NAME_HIDE, AddressLock.NAME_SHOW);
		ipaddresshider$install(this.addressField, this.ipaddresshider$address,
			AddressLock.ADDRESS_HIDE, AddressLock.ADDRESS_SHOW);
	}

	@Unique
	private void ipaddresshider$install(TextFieldWidget field, LockState state,
	                                    String hideKey, String showKey) {
		AddressLock lock = AddressLock.install(field, this.textRenderer,
			hideKey, showKey,
			true, state.marked, state.stars, state.initialText,
			() -> state.typed,
			() -> state.typed = true,
			marked -> state.marked = marked);
		this.addDrawableChild(lock.button());
	}
}
