package fr.heetstudio.confee.ui.screen;

import fr.heetstudio.confee.ConfeeLog;
import fr.heetstudio.confee.api.Theme;
import fr.heetstudio.confee.api.client.OptionWidget;
import fr.heetstudio.confee.core.Option;
import fr.heetstudio.confee.core.Validated;
import fr.heetstudio.confee.ui.EditSession;
import fr.heetstudio.confee.ui.Ui;
import fr.heetstudio.confee.ui.render.Draw;
import fr.heetstudio.confee.ui.widget.ConfeeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Puts a mod's {@link OptionWidget} in the screen. Its value goes through the edit session (validated, saved with
 * "Save"). Any exception of the mod's code is caught: the widget is then replaced by an error message (spec 14).
 * <p>Internal: may change without notice.</p>
 */
final class CustomWidgetAdapter extends ConfeeWidget {

    private final OptionWidget<Object> widget;
    private final Option<Object> option;
    private boolean broken;

    @SuppressWarnings("unchecked")
    CustomWidgetAdapter(Ui ui, EditSession session, Option<?> option, Component label, OptionWidget<?> widget) {
        super(ui, 100, 20);
        this.widget = (OptionWidget<Object>) widget;
        this.option = (Option<Object>) option;
        CustomWidgetAdapter self = this;
        this.widget.bind(new OptionWidget.Context<>() {
            @Override
            public Object get() {
                return session.get(self.option);
            }

            @Override
            public boolean set(Object value) {
                Validated<Object> check = self.option.validate(value);
                if (!check.isOk()) {
                    return false;
                }
                session.set(self.option, check.value());
                return true;
            }

            @Override
            public Object defaultValue() {
                return self.option.getDefault();
            }

            @Override
            public Number minimum() {
                return self.option.min();
            }

            @Override
            public Number maximum() {
                return self.option.max();
            }

            @Override
            public Theme theme() {
                return ui.theme();
            }

            @Override
            public Font font() {
                return ui.font();
            }

            @Override
            public Component label() {
                return label;
            }

            @Override
            public boolean focused() {
                return self.focused;
            }

            @Override
            public boolean enabled() {
                return self.enabled;
            }

            @Override
            public void click() {
                ui.click();
            }
        });
    }

    private void fail(Throwable e) {
        if (!broken) {
            broken = true;
            ConfeeLog.error("Custom widget of option '" + option.path() + "' failed; it is disabled",
                    e instanceof RuntimeException re ? re : new RuntimeException(e));
        }
    }

    @Override
    public int heightFor(int width) {
        if (broken) {
            return 12;
        }
        try {
            return Math.max(8, Math.min(400, widget.height(width)));
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return 12;
        }
    }

    @Override
    public boolean isFocusable() {
        if (broken || !super.isFocusable()) {
            return false;
        }
        try {
            return widget.focusable();
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) {
            return;
        }
        if (broken) {
            Draw.textFit(g, ui.font(), Component.translatable("confee.widget.error"), x, y + 2, width, ui.theme().error());
            return;
        }
        try {
            widget.place(x, y, width, height);
            widget.render(g, x, y, width, height, mouseX, mouseY, delta);
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
        if (focused) {
            Draw.focusRing(g, x, y, width, height, ui.theme().accent());
        }
    }

    private interface Call {
        boolean run();
    }

    private boolean safely(Call call) {
        if (broken || !enabled) {
            return false;
        }
        try {
            return call.run();
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return contains(mx, my) && safely(() -> widget.mouseClicked(mx, my, button));
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return safely(() -> widget.mouseReleased(mx, my, button));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return safely(() -> widget.mouseDragged(mx, my, button, dx, dy));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        return safely(() -> widget.mouseScrolled(mx, my, delta));
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return safely(() -> widget.keyPressed(key, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return safely(() -> widget.charTyped(c, modifiers));
    }

    @Override
    public Component narration() {
        if (broken) {
            return Component.translatable("confee.widget.error");
        }
        try {
            return widget.narration();
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return Component.translatable("confee.widget.error");
        }
    }
}
