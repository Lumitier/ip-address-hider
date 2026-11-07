package com.lumitier.ipaddresshider.client;

/**
 * Was ein Schloss ueber den Neuaufbau seines Bildschirms hinweg wissen muss.
 *
 * WARUM ES DAS GIBT: init() laeuft nicht nur beim Oeffnen, sondern bei
 * jeder Fenstergroessenaenderung erneut (F11, Vollbild), und AddressLock
 * entsteht dabei jedes Mal neu. Alles, was ein solches Neuaufbauen
 * ueberstehen muss, liegt deshalb hier - und dieses Objekt haengt am
 * Bildschirm, nicht am Schloss. Genau diese Trennung hat die Fehler mit
 * dem aufspringenden Schloss und den falschen Sternchenzahlen behoben.
 *
 * Seit es zwei Schloesser pro Bildschirm gibt (Name und Adresse), haelt
 * jeder Bildschirm ein Objekt pro Feld statt fuenf Einzelfelder doppelt.
 */
public final class LockState {

	/** Schon einmal initialisiert? Danach gilt, was hier steht. */
	public boolean known;

	/** Vom Nutzer zugesperrt (ohne Streamer-Modus). */
	public boolean marked;

	/** Gewuerfelte Sternchenzahl fuer dieses Oeffnen des Bildschirms. */
	public int stars;

	/** Feldinhalt beim ERSTEN Oeffnen - daran wird Tippen erkannt. */
	public String initialText = "";

	/** Schon getippt? Einmal gesetzt, nie zurueckgenommen. */
	public boolean typed;

	/**
	 * Beim ersten Aufruf befuellen, danach nichts mehr tun.
	 *
	 * @param text   aktueller Feldinhalt
	 * @param marked gespeicherter Schlosszustand aus der Config
	 */
	public void firstOpen(String text, boolean marked) {
		if (this.known) {
			return;
		}
		this.marked = marked;
		this.stars = Mask.randomLength();
		this.initialText = text == null ? "" : text;
		this.known = true;
	}
}
