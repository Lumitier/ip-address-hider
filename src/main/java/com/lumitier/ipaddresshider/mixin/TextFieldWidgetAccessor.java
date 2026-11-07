package com.lumitier.ipaddresshider.mixin;

import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Liest die Scrollposition eines Textfelds. Nur lesen, nichts aendern.
 *
 * WOZU: TextFieldWidget ruft seine Formatierer pro Bild ZWEIMAL - einmal
 * fuer den sichtbaren Text vor dem Cursor (zweites Argument: diese
 * Scrollposition) und einmal fuer den danach (zweites Argument: die
 * Cursorposition). Die gewuerfelte Sternchenzahl darf aber nur EINMAL
 * gezeichnet werden. Welcher Aufruf der erste ist, verraet allein dieser
 * Wert, und Minecraft bietet ihn nicht oeffentlich an.
 */
@Mixin(TextFieldWidget.class)
public interface TextFieldWidgetAccessor {

	@Accessor("firstCharacterIndex")
	int ipaddresshider$getFirstCharacterIndex();
}
