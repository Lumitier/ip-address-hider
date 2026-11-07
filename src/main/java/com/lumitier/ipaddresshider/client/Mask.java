package com.lumitier.ipaddresshider.client;

import net.minecraft.client.font.TextRenderer;

import java.util.random.RandomGenerator;

/**
 * Baut die Sternchenkette, die statt der Adresse dasteht.
 *
 * WARUM NICHT EINFACH EIN STERN PRO ZEICHEN:
 * Dann steht die Laenge der Adresse auf dem Bildschirm. "mc.hypixel.net"
 * und eine 14-stellige private Adresse saehen gleich aus, aber eine
 * vierstellige waere sofort als kurz erkennbar, und bei einer Adresse mit
 * Port liesse sich mitzaehlen. Die Laenge ist die halbe Information.
 *
 * WANN GEWUERFELT WIRD:
 * Einmal, wenn der Bildschirm aufgeht. Nicht bei jedem Zeichnen - das
 * laeuft mehrfach pro Sekunde und die Zeile wuerde flackern. Und nicht
 * fest aus der Adresse abgeleitet: Eine Laenge, die bei demselben Server
 * immer gleich ist, ist ein Wiedererkennungsmerkmal. Jedes Oeffnen eine
 * neue Laenge verraet ueber mehrere Aufnahmen hinweg am wenigsten.
 */
public final class Mask {

	private static final char STAR = '*';

	private static final RandomGenerator RANDOM = new java.util.Random();

	/** Untergrenze aus der Vorgabe. */
	public static final int MIN = 7;
	/** Obergrenze aus der Vorgabe. */
	public static final int MAX = 40;

	private Mask() {}

	/**
	 * Eine neue Laenge wuerfeln - EINMAL pro Oeffnen des Bildschirms.
	 *
	 * Nicht pro Bild: Die Formatierung laeuft bei jedem Zeichnen, also
	 * mehrfach pro Sekunde. Wuerde dort gewuerfelt, flackerte die Zeile
	 * und saehe aus wie ein Fehler. Wer den Bildschirm neu oeffnet,
	 * bekommt eine neue Laenge; wer nur das Fenster umschaltet, behaelt
	 * seine.
	 */
	public static int randomLength() {
		return MIN + RANDOM.nextInt(MAX - MIN + 1);
	}

	/**
	 * Sternchen in der gewuenschten Zahl, gekuerzt auf das, was ins Feld
	 * passt.
	 *
	 * Die Obergrenze von 40 passt bei 200 Pixeln Feldbreite nicht hinein,
	 * es sind rund 32. Was nicht hineinpasst, wuerde ueber den Rand
	 * hinaus gezeichnet - der Formatierer bekommt vom Textfeld bereits
	 * den sichtbaren Ausschnitt und kuerzt selbst nichts nach.
	 */
	/** Untergrenze fuer Namen in der Serverliste - laut Vorgabe null. */
	public static final int LIST_MIN = 0;
	/** Obergrenze fuer Namen in der Serverliste. */
	public static final int LIST_MAX = 40;

	/**
	 * Laenge fuer einen verdeckten Namen in der Serverliste.
	 *
	 * Eigene Spanne, weil die Vorgabe dort 0 bis 40 lautet statt 7 bis 40.
	 * Bei 0 steht die Namenszeile leer da - gewollt, aber man sollte es
	 * wissen, bevor man es fuer einen Fehler haelt.
	 *
	 * Gewuerfelt wird einmal pro Listeneintrag. Minecraft baut die Eintraege
	 * bei jedem Refresh neu (refresh() oeffnet einen neuen
	 * MultiplayerScreen), also wird genau dann neu gewuerfelt.
	 */
	public static int randomListLength() {
		return LIST_MIN + RANDOM.nextInt(LIST_MAX - LIST_MIN + 1);
	}

	public static String stars(int count) {
		return String.valueOf(STAR).repeat(Math.max(0, count));
	}

	/**
	 * Ein Sternchen je Zeichen - fuer das Feld, in dem gerade getippt
	 * wird.
	 *
	 * Hier wird NICHT gewuerfelt und NICHT auf sieben aufgefuellt: Wer
	 * tippt, muss sehen, wie viel er getippt hat, und die Schreibmarke
	 * muss hinter dem letzten Zeichen stehen. Minecraft berechnet die
	 * Markenposition aus dem GEZEICHNETEN Text, ein Stern pro Zeichen
	 * passt also exakt.
	 *
	 * Uebergeben wird der sichtbare Ausschnitt, den das Textfeld selbst
	 * zurechtgeschnitten hat - damit stimmt es auch, wenn das Feld
	 * waehrend des Tippens scrollt.
	 */
	public static String perCharacter(String visible) {
		return String.valueOf(STAR).repeat(visible.length());
	}

	public static String of(TextRenderer tr, int requested, int availableWidth) {
		int starWidth = Math.max(1, tr.getWidth(String.valueOf(STAR)));
		int fits = Math.max(MIN, availableWidth / starWidth);
		int n = Math.max(MIN, Math.min(requested, Math.min(MAX, fits)));
		return String.valueOf(STAR).repeat(n);
	}
}
