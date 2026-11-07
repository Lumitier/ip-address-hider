package com.lumitier.ipaddresshider.client;

import com.lumitier.ipaddresshider.IpAddressHider;
import com.lumitier.ipaddresshider.mixin.TextFieldWidgetAccessor;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.function.Consumer;

/**
 * Das Verdecken eines Adressfeldes - einmal geschrieben, von beiden
 * Bildschirmen benutzt (Server bearbeiten und Direktverbindung).
 *
 * Die Mixins bleiben damit winzig: Sie reichen ihr Feld herein, haengen
 * den Knopf in den Bildschirm und sind fertig. Jede Regel darueber, WANN
 * verdeckt wird, steht hier an einer Stelle statt zweimal nebeneinander.
 *
 * NUR DAS SCHLOSS ZEIGT DIE ADRESSE. Es gab eine Fassung, in der die
 * Direktverbindung die Adresse sichtbar liess, solange das Feld den
 * Fokus hatte - damit man ueberhaupt tippen kann. Das war falsch: Ein
 * Klick ins Feld hat die Adresse aufgedeckt, und nach F11 setzt
 * setInitialFocus() den Fokus von selbst dorthin zurueck. Verdeckt
 * heisst verdeckt, egal wo der Fokus steht.
 *
 * TIPPEN GEHT TROTZDEM. Im verdeckten Feld wird jedes Zeichen als
 * Sternchen gezeichnet, wie in einem Passwortfeld - man sieht, wie
 * viel man geschrieben hat, aber nicht was. Verlaesst man das Feld,
 * tritt wieder die gewuerfelte Laenge an die Stelle.
 */
public final class AddressLock {

	private final TextFieldWidget field;
	private final TextRenderer textRenderer;

	/**
	 * Gilt der Streamer-Modus fuer dieses Feld?
	 *
	 * Bei gespeicherten Servern ja. Bei der Direktverbindung NICHT - dort
	 * tippt man die Adresse gerade erst ein, und ein Schloss, das sich
	 * nicht oeffnen laesst, machte den Bildschirm unbenutzbar. Dort ist
	 * das Schloss rein manuell: tippen, zusperren, verbinden.
	 */
	private final boolean obeysStreamerMode;

	/** Meldet jede Aenderung an den Bildschirm, der sie ueberlebt. */
	private final Consumer<Boolean> onChanged;

	/** Zahl der Sternchen, einmal pro Oeffnen gewuerfelt. */
	private final int stars;

	/**
	 * Text, der beim ERSTEN Oeffnen des Bildschirms im Feld stand.
	 *
	 * Daran wird erkannt, ob der Nutzer getippt hat - und DAS, nicht der
	 * Fokus, entscheidet ueber die Zahl der Sternchen. Der Fokus taugt
	 * nicht: DirectConnectScreen setzt ihn in setInitialFocus() von
	 * selbst auf die Adresszeile, dort waere also schon beim Oeffnen die
	 * echte Laenge zu sehen gewesen. AddServerScreen fokussiert dagegen
	 * das Namensfeld. Zwei Bildschirme, zwei Verhalten, beides falsch.
	 *
	 * WICHTIG: Der Wert kommt vom Bildschirm, nicht von diesem Objekt.
	 * AddressLock entsteht bei jedem Neuaufbau neu - haette es den Text
	 * selbst gelesen, haette es nach dem ersten Tastendruck die gerade
	 * getippte Eingabe fuer den Ausgangstext gehalten und waere wieder
	 * auf die gewuerfelte Laenge zurueckgefallen.
	 */
	private final String initialText;

	/**
	 * Hat der Nutzer in diesem Bildschirm schon getippt? EINMAL gesetzt,
	 * nie zurueckgenommen - sonst springt die Zahl mitten im Tippen auf
	 * die Zufallslaenge zurueck, sobald die Eingabe zufaellig wieder dem
	 * Ausgangstext gleicht. Lebt wie initialText im Bildschirm.
	 */
	private final java.util.function.BooleanSupplier typed;
	private final Runnable markTyped;

	/** Uebersetzungsschluessel fuer den Tooltip - Adresse oder Name. */
	private final String hideKey;
	private final String showKey;

	private boolean marked;
	private LockButton button;

	private AddressLock(TextFieldWidget field, TextRenderer textRenderer,
	                    String hideKey, String showKey,
	                    boolean obeysStreamerMode, int stars,
	                    String initialText,
	                    java.util.function.BooleanSupplier typed, Runnable markTyped,
	                    Consumer<Boolean> onChanged) {
		this.field = field;
		this.textRenderer = textRenderer;
		this.hideKey = hideKey;
		this.showKey = showKey;
		this.obeysStreamerMode = obeysStreamerMode;
		this.stars = stars;
		this.initialText = initialText == null ? "" : initialText;
		this.typed = typed;
		this.markTyped = markTyped;
		this.onChanged = onChanged;
	}

	/**
	 * Haengt den Formatierer ans Feld und baut den Knopf.
	 *
	 * @param marked der zuletzt bekannte Zustand. Beim ersten Oeffnen
	 *               kommt er aus der Config, bei jedem Neuaufbau des
	 *               Bildschirms (Fenstergroesse, F11, Vollbild) aus dem
	 *               Bildschirm selbst - sonst springt das Schloss auf,
	 *               weil refreshWidgetPositions() intern clearAndInit()
	 *               ruft und init() damit erneut durchlaeuft.
	 * @param stars  gewuerfelte Laenge, ebenfalls vom Bildschirm gemerkt
	 */
	public static AddressLock install(TextFieldWidget field, TextRenderer textRenderer,
	                                  String hideKey, String showKey,
	                                  boolean obeysStreamerMode,
	                                  boolean marked, int stars,
	                                  String initialText,
	                                  java.util.function.BooleanSupplier typed,
	                                  Runnable markTyped,
	                                  Consumer<Boolean> onChanged) {
		AddressLock lock = new AddressLock(field, textRenderer, hideKey, showKey,
			obeysStreamerMode, stars, initialText, typed, markTyped, onChanged);
		lock.marked = marked;

		field.addFormatter((visible, firstCharacterIndex) -> {
			if (!lock.closed()) {
				// null heisst: Minecraft zeichnet weiter wie immer.
				return null;
			}
			// ZWEI LAENGEN, und der Unterschied ist Absicht:
			//
			// Solange der Nutzer nichts eingegeben hat, eine gewuerfelte
			// Laenge. Das ist der Zustand, in dem die Adresse lange auf
			// dem Bildschirm steht - dann soll nicht einmal die Zahl der
			// Zeichen etwas verraten.
			//
			// Sobald er tippt, ein Stern pro Zeichen. Sonst wuesste er
			// nicht, wo er ist, und die Schreibmarke stuende irgendwo.
			if (!lock.typed.getAsBoolean()
					&& !field.getText().equals(lock.initialText)) {
				lock.markTyped.run();
			}

			String shown;
			if (lock.typed.getAsBoolean()) {
				// Pro Teilstueck ein Stern je Zeichen - die beiden
				// Aufrufe ergeben zusammen genau die richtige Laenge.
				shown = Mask.perCharacter(visible);
			} else if (visible.isEmpty()
					|| firstCharacterIndex != ((TextFieldWidgetAccessor) field)
						.ipaddresshider$getFirstCharacterIndex()) {
				// Der zweite Aufruf (Text hinter dem Cursor) bekommt
				// nichts. Sonst stuende die gewuerfelte Kette zweimal da
				// - genau der Fehler mit den "40 Sternchen".
				shown = "";
			} else {
				shown = Mask.of(lock.textRenderer, lock.stars, field.getInnerWidth());
			}
			return OrderedText.styledForwardsVisitedString(shown, Style.EMPTY);
		});

		lock.button = new LockButton(
			field.getX() + field.getWidth() + LockButton.GAP,
			field.getY(),
			field.getHeight(),
			lock::closed,
			b -> lock.toggle());

		lock.apply();
		return lock;
	}

	/** Tooltip-Schluessel fuer das Adressfeld. */
	public static final String ADDRESS_HIDE = "ipaddresshider.button.hide";
	public static final String ADDRESS_SHOW = "ipaddresshider.button.show";
	/** Tooltip-Schluessel fuer das Namensfeld. */
	public static final String NAME_HIDE = "ipaddresshider.button.name.hide";
	public static final String NAME_SHOW = "ipaddresshider.button.name.show";

	public LockButton button() {
		return this.button;
	}

	/** Zeigt das Schloss geschlossen - und wird damit verdeckt gezeichnet? */
	public boolean closed() {
		return this.marked || (this.obeysStreamerMode && IpAddressHider.streamerMode());
	}

	/** Kann der Nutzer das Schloss gerade bedienen? */
	private boolean toggleable() {
		return !(this.obeysStreamerMode && IpAddressHider.streamerMode());
	}

	private void toggle() {
		if (!toggleable()) {
			return;
		}
		// NUR UMSCHALTEN, NICHT SPEICHERN.
		//
		// Wer speichert, entscheidet der Bildschirm. Beim Bearbeiten
		// eines Servers passiert das erst auf "Fertig", mit der
		// Adresse, die dann wirklich dasteht. Frueher schrieb das
		// Schloss sofort - und bei leerem Feld gab es nichts zu
		// schreiben, der Knopf tat also scheinbar gar nichts.
		this.marked = !this.marked;
		if (this.onChanged != null) {
			this.onChanged.accept(this.marked);
		}
		apply();
	}

	/**
	 * Feldsperre, Knopfzustand und Beschriftung an den Zustand anpassen.
	 *
	 * Das Feld selbst wird nicht mehr angefasst - siehe unten.
	 */
	public void apply() {
		boolean closed = closed();

		// DAS FELD BLEIBT BESCHREIBBAR, auch verdeckt.
		//
		// Eine frueherer Fassung hat es gesperrt, damit die
		// Schreibmarke nichts ueber die Laenge verraet. Das ging zu
		// weit: Im Streamer-Modus liess sich dann keine Adresse mehr
		// eintippen. Stattdessen zeichnet der Formatierer beim Tippen
		// ein Sternchen pro Zeichen - die Marke sitzt richtig, und
		// gelesen werden kann trotzdem nichts.

		if (this.button == null) {
			return;
		}

		this.button.active = toggleable();

		Text label;
		if (!toggleable()) {
			label = Text.translatable("ipaddresshider.button.locked_by_streamer_mode");
		} else if (closed) {
			label = Text.translatable(this.showKey);
		} else {
			label = Text.translatable(this.hideKey);
		}
		// Nur die Beschriftung setzen - den Tooltip zeichnet LockButton
		// selbst aus genau diesem Text, damit beide Schloesser ihn gleich
		// platzieren. Die Beschriftung dient zugleich der Vorlesefunktion.
		this.button.setMessage(label);
	}
}
