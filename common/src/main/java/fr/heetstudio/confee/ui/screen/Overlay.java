package fr.heetstudio.confee.ui.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Layer drawn over a screen that takes all input while open (dropdown popup, modal dialog).
 * <p>Internal: may change without notice.</p>
 */
public interface Overlay {

    void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta);

    boolean mouseClicked(double mx, double my, int button);

    default boolean mouseReleased(double mx, double my, int button) {
        return true;
    }

    default boolean mouseScrolled(double mx, double my, double delta) {
        return true;
    }

    default boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return true;
    }

    boolean keyPressed(int key, int scanCode, int modifiers);

    default boolean charTyped(char c, int modifiers) {
        return true;
    }

    /** Narration of the focused element of the overlay. */
    Component narration();

    @Nullable
    default Component tooltip(double mx, double my) {
        return null;
    }

    /** Called when the overlay is removed. */
    default void closed() {
    }
}
