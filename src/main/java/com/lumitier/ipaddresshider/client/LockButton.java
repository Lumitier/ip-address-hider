package com.lumitier.ipaddresshider.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.util.Identifier;
import org.joml.Vector2i;

import java.util.function.BooleanSupplier;

import static com.lumitier.ipaddresshider.IpAddressHider.MOD_ID;

/**
 * Quadratischer Knopf neben dem Adressfeld, der ein Schloss zeigt.
 *
 * WARUM EIN EIGENER KNOPF UND NICHT TexturedButtonWidget:
 * TexturedButtonWidget zeichnet den GANZEN Knopf aus einer Textur - also
 * auch Rahmen, Hintergrund und die Zustaende normal/ueberfahren/gesperrt.
 * Dafuer muessten vier Knopfgrafiken mitgeliefert werden, die bei jedem
 * Resourcepack falsch aussehen. Hier zeichnet Minecraft den Knopf wie
 * jeden anderen, und darauf kommt nur das Schloss. Das passt sich
 * automatisch jedem Pack an.
 *
 * WARUM drawIcon UND NICHT renderWidget:
 * PressableWidget hat renderWidget als final markiert und ruft von dort
 * erst drawButton und dann drawIcon. drawIcon ist also die vorgesehene
 * Stelle - ein Mixin oder ein Trick waere hier unnoetig.
 *
 * WARUM DIE BESCHRIFTUNG LEER BLEIBT:
 * Damit der Knopf quadratisch sein kann und genau so hoch wie das
 * Adressfeld. Einen sprechenden Namen hat er trotzdem - setMessage setzt
 * ihn fuer Tooltip und Bildschirmausgabe, gemalt wird er nicht.
 *
 * ACHTUNG BEIM ERWEITERN: ButtonWidget hat eine innere Klasse namens
 * Text. In dieser Datei verdeckt die jeden Import von net.minecraft.text
 * .Text - deshalb steht der Typ unten ausgeschrieben.
 */
public final class LockButton extends ButtonWidget {

	private static final Identifier CLOSED = Identifier.of(MOD_ID, "lock_closed");
	private static final Identifier OPEN = Identifier.of(MOD_ID, "lock_open");

	/** Kantenlaenge des Schlosses im Knopf. */
	private static final int ICON = 16;

	/**
	 * Abstand zwischen Adressfeld und Knopf.
	 *
	 * Das Feld steht bei width/2-100 und ist 200 breit, der Knopf also bei
	 * width/2+104. Bei der schmalsten ueblichen Oberflaechenbreite von 320
	 * endet er bei 284 und bleibt damit im Bild.
	 */
	public static final int GAP = 4;

	/**
	 * Abdunklung fuer den gesperrten Knopf.
	 *
	 * Minecraft blendet bei einem inaktiven Knopf nur Rahmen und
	 * Beschriftung ab; ein selbst gezeichnetes Symbol bliebe hell und
	 * saehe damit anklickbar aus. Derselbe Wert, den Vanilla fuer
	 * abgeblendeten Text benutzt.
	 */
	private static final int DIMMED = 0xFFA0A0A0;

	/**
	 * Abstand Tooltip zu Mauszeiger und Mindestabstand zum Bildschirmrand
	 * - dieselben Werte, die Minecraft selbst benutzt.
	 */
	private static final int TIP_OFFSET = 12;
	private static final int TIP_MARGIN = 5;

	/**
	 * Tooltip-Platzierung fuer BEIDE Schloesser gleich.
	 *
	 * WARUM NICHT setTooltip(): Minecraft setzt einen Tooltip rechts neben
	 * die Maus, klappt ihn aber auf die LINKE Seite, sobald er rechts ueber
	 * den Rand ragen wuerde. Der Text am Namensschloss ist ein paar Pixel
	 * breiter als der am Adressschloss - je nach Fenstergroesse sprang der
	 * eine nach links, der andere nicht. Zwei gleiche Knoepfe, zwei
	 * verschiedene Verhalten.
	 *
	 * Hier: immer rechts der Maus. Wird es eng, wird der Tooltip nur so
	 * weit nach links geschoben wie noetig, statt die Seite zu wechseln.
	 */
	private static final TooltipPositioner POSITIONER = (screenW, screenH, x, y, w, h) -> {
		int px = Math.max(TIP_MARGIN, Math.min(x + TIP_OFFSET, screenW - TIP_MARGIN - w));
		int py = Math.max(TIP_MARGIN, Math.min(y - TIP_OFFSET, screenH - TIP_MARGIN - h));
		return new Vector2i(px, py);
	};

	private final BooleanSupplier closed;

	public LockButton(int x, int y, int size, BooleanSupplier closed, PressAction onPress) {
		super(x, y, size, size, net.minecraft.text.Text.empty(), onPress,
			DEFAULT_NARRATION_SUPPLIER);
		this.closed = closed;
	}

	@Override
	protected void drawIcon(DrawContext ctx, int mouseX, int mouseY, float delta) {
		int ix = getX() + (getWidth() - ICON) / 2;
		int iy = getY() + (getHeight() - ICON) / 2;
		ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED,
			this.closed.getAsBoolean() ? CLOSED : OPEN,
			ix, iy, ICON, ICON, this.active ? -1 : DIMMED);

		// Tooltip selbst zeichnen - siehe POSITIONER. drawTooltip ist
		// verzoegert: Minecraft malt ihn am Ende des Bildes ueber alles.
		// Die Zeilen entstehen nur, solange die Maus wirklich darauf
		// steht, nicht in jedem Bild.
		if (isHovered()) {
			MinecraftClient client = MinecraftClient.getInstance();
			ctx.drawTooltip(client.textRenderer, Tooltip.wrapLines(client, getMessage()),
				POSITIONER, mouseX, mouseY, false);
		}
	}
}
