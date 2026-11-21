package fr.heetstudio.confee.testmod;

import fr.heetstudio.confee.api.client.ConfeeDraw;
import fr.heetstudio.confee.api.client.ConfeeIcons;
import fr.heetstudio.confee.api.client.OptionWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom widget using every {@link OptionWidget} hook (client only): one line per item with its icon and a "×"
 * button ({@link ConfeeDraw}, {@link ConfeeIcons}), Up/Down to move between lines (only the focused line is scrolled
 * into view: {@link #focusBounds()}), "+ Add" opening a list drawn over the next options ({@link #renderOverlay}) where
 * typing filters the items ({@link #capturesText()}). Enter adds the typed text even when it is not an item: the line
 * then shows {@link #error()} and "Save" is disabled.
 */
public class LootLinesWidget extends OptionWidget<List<String>> {

    private static final int LINE = 18;
    private static final int ADD_H = 20;
    private static final int SHOWN = 6;

    /** Focused line, or -1 for the "+ Add" button. */
    private int selected = -1;
    private boolean adding;
    private String filter = "";
    private boolean pressedRemove;

    @Override
    public int height(int width) {
        return value().size() * LINE + ADD_H + 2;
    }

    private int addY() {
        return y() + value().size() * LINE + 2;
    }

    private int removeX() {
        return x() + width() - 16;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY, float delta) {
        List<String> items = value();
        for (int i = 0; i < items.size(); i++) {
            int ly = y + i * LINE;
            String id = items.get(i);
            if (focused() && i == selected) {
                g.fill(x, ly, x + width, ly + LINE - 1, theme().panelBgDeep());
            }
            if (!ConfeeIcons.drawItem(g, id, x + 1, ly + 1, 16)) {
                g.fill(x + 1, ly + 1, x + 17, ly + 17, theme().error());
            }
            Component name = ConfeeIcons.name("minecraft:item", id);
            ConfeeDraw.textFit(g, name != null ? name : Component.literal(id), x + 22, ly + 5, width - 44,
                    known(id) ? theme().textOnDark() : theme().error());
            boolean hover = mouseX >= removeX() && mouseX < removeX() + 16 && mouseY >= ly && mouseY < ly + 16;
            ConfeeDraw.button(g, theme(), removeX(), ly, 16, 16, Component.literal("×"), hover, hover && pressedRemove, enabled());
        }
        int ay = addY();
        boolean hoverAdd = mouseX >= x && mouseX < x + 90 && mouseY >= ay && mouseY < ay + ADD_H;
        ConfeeDraw.button(g, theme(), x, ay, 90, ADD_H, Component.literal(adding ? "Type to filter" : "+ Add an item"),
                hoverAdd, false, enabled());
        if (focused() && selected < 0) {
            ConfeeDraw.focusRing(g, theme(), x, ay, 90, ADD_H);
        }
    }

    // ------------------------------------------------------------------ overlay (suggestions over the next options)

    private List<String> matches() {
        List<String> out = new ArrayList<>();
        String f = filter.toLowerCase(Locale.ROOT);
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
            if (id.toString().contains(f) && !value().contains(id.toString())) {
                out.add(id.toString());
                if (out.size() == SHOWN) {
                    break;
                }
            }
        }
        return out;
    }

    @Override
    public int[] overlayBounds() {
        return adding ? new int[]{x(), addY() + ADD_H, Math.min(width(), 200), 14 + SHOWN * LINE} : null;
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int[] r = overlayBounds();
        if (r == null) {
            return;
        }
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], theme().outline());
        g.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, theme().panelBgDeep());
        ConfeeDraw.text(g, Component.literal("> " + filter + "_"), r[0] + 4, r[1] + 3, theme().accent());
        List<String> found = matches();
        for (int i = 0; i < found.size(); i++) {
            int ly = r[1] + 13 + i * LINE;
            if (mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= ly && mouseY < ly + LINE) {
                g.fill(r[0] + 1, ly, r[0] + r[2] - 1, ly + LINE, theme().panelBg());
            }
            ConfeeIcons.drawItem(g, found.get(i), r[0] + 3, ly + 1, 16);
            ConfeeDraw.textFit(g, Component.literal(found.get(i)), r[0] + 22, ly + 5, r[2] - 26, theme().textOnDark());
        }
    }

    @Override
    public boolean overlayClicked(double mouseX, double mouseY, int button) {
        int[] r = overlayBounds();
        int index = (int) ((mouseY - r[1] - 13) / LINE);
        List<String> found = matches();
        if (mouseY >= r[1] + 13 && index >= 0 && index < found.size()) {
            add(found.get(index));
        }
        return true;
    }

    @Override
    public boolean capturesText() {
        return adding;
    }

    private void add(String id) {
        List<String> next = new ArrayList<>(value());
        next.add(id);
        if (setValue(next)) {
            playClick();
        }
        adding = false;
        filter = "";
    }

    // ------------------------------------------------------------------ focus, error

    @Override
    public int[] focusBounds() {
        return selected >= 0 ? new int[]{x(), y() + selected * LINE, width(), LINE}
                : new int[]{x(), addY(), width(), ADD_H + (adding ? 14 + SHOWN * LINE : 0)};
    }

    @Override
    public Component error() {
        for (String id : value()) {
            if (!known(id)) {
                return Component.literal("Unknown item: " + id);
            }
        }
        return null;
    }

    private static boolean known(String id) {
        Identifier rl = Identifier.tryParse(id);
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        List<String> items = value();
        int line = (int) ((mouseY - y()) / LINE);
        if (mouseY >= y() && line < items.size()) {
            selected = line;
            adding = false;
            if (mouseX >= removeX()) {
                pressedRemove = true;
                List<String> next = new ArrayList<>(items);
                next.remove(line);
                setValue(next);
                playClick();
                selected = Math.min(selected, next.size() - 1);
            }
            return true;
        }
        if (mouseY >= addY() && mouseX < x() + 90) {
            selected = -1;
            adding = !adding;
            filter = "";
            playClick();
            revealFocus();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressedRemove = false;
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl) {
            return false; // Ctrl+Tab, Ctrl+F... stay screen shortcuts
        }
        if (adding) {
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE -> adding = false;
                case GLFW.GLFW_KEY_BACKSPACE -> filter = filter.isEmpty() ? "" : filter.substring(0, filter.length() - 1);
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (!filter.isEmpty()) {
                        add(filter.contains(":") ? filter : "minecraft:" + filter);
                    }
                }
                default -> {
                    return true; // letters arrive through charTyped
                }
            }
            return true;
        }
        int size = value().size();
        switch (key) {
            case GLFW.GLFW_KEY_UP -> selected = selected < 0 ? size - 1 : Math.max(0, selected - 1);
            case GLFW.GLFW_KEY_DOWN -> selected = selected < 0 || selected >= size - 1 ? -1 : selected + 1;
            case GLFW.GLFW_KEY_DELETE -> {
                if (selected >= 0 && selected < size) {
                    List<String> next = new ArrayList<>(value());
                    next.remove(selected);
                    setValue(next);
                    selected = Math.min(selected, next.size() - 1);
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (selected >= 0) {
                    return false;
                }
                adding = true;
                filter = "";
                playClick();
            }
            default -> {
                return false;
            }
        }
        revealFocus();
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (adding && (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '/' || c == '.')) {
            filter += Character.toLowerCase(c);
            return true;
        }
        return false;
    }

    @Override
    public Component narration() {
        return selected >= 0 && selected < value().size() ? Component.literal(value().get(selected))
                : Component.literal("Add an item");
    }
}
