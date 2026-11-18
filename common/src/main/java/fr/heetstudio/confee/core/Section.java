package fr.heetstudio.confee.core;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Group of options (a category, a TOML table, a JSON object...). Children keep declaration order.
 * <p>Internal: may change without notice.</p>
 */
public final class Section implements Node {

    private final String key;
    @Nullable
    private final String displayName;
    @Nullable
    private String comment;
    private final List<Node> children = new ArrayList<>();
    private final List<Placed> decorations = new ArrayList<>();
    @Nullable
    private String icon;
    private String path = "";

    /** A decoration and the index of the child it is shown before ({@code children().size()} = at the end). */
    public record Placed(int index, fr.heetstudio.confee.api.Decoration decoration) {
    }

    public Section(String key, @Nullable String displayName, @Nullable String comment) {
        this.key = key;
        this.displayName = displayName;
        this.comment = comment;
    }

    /** Creates the unnamed root section of a schema. */
    public static Section root(@Nullable String comment) {
        return new Section("", null, comment);
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    @Nullable
    public String displayName() {
        return displayName;
    }

    @Override
    @Nullable
    public String comment() {
        return comment;
    }

    public void setComment(@Nullable String comment) {
        this.comment = comment;
    }

    /** Dotted path from the root ({@code ""} for the root). */
    public String path() {
        return path;
    }

    /** Icon (item ID or texture) shown on the category button / navigation row, or null. */
    @Nullable
    public String icon() {
        return icon;
    }

    public void setIcon(@Nullable String icon) {
        this.icon = icon == null || icon.isBlank() ? null : icon;
    }

    /** Adds a decoration after the children declared so far. */
    public void addDecoration(fr.heetstudio.confee.api.Decoration decoration) {
        decorations.add(new Placed(children.size(), decoration));
    }

    public List<Placed> decorations() {
        return Collections.unmodifiableList(decorations);
    }

    public boolean isRoot() {
        return key.isEmpty();
    }

    public List<Node> children() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Appends a child and assigns its path.
     *
     * @throws IllegalArgumentException if a child with the same key already exists
     */
    public <N extends Node> N add(N child) {
        if (find(child.key()).isPresent()) {
            throw new IllegalArgumentException("Duplicate key '" + child.key() + "' in section '" + path + "'");
        }
        String childPath = path.isEmpty() ? child.key() : path + "." + child.key();
        if (child instanceof Section section) {
            section.setPath(childPath);
        } else if (child instanceof Option<?> option) {
            option.setPath(childPath);
        }
        children.add(child);
        return child;
    }

    public Optional<Node> find(String childKey) {
        for (Node child : children) {
            if (child.key().equals(childKey)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    /** Looks up an option by dotted path relative to this section. */
    public Optional<Option<?>> findOption(String dottedPath) {
        int dot = dottedPath.indexOf('.');
        String head = dot < 0 ? dottedPath : dottedPath.substring(0, dot);
        Optional<Node> node = find(head);
        if (node.isEmpty()) {
            return Optional.empty();
        }
        if (dot < 0) {
            return node.get() instanceof Option<?> option ? Optional.of(option) : Optional.empty();
        }
        return node.get() instanceof Section section
                ? section.findOption(dottedPath.substring(dot + 1))
                : Optional.empty();
    }

    /** Visits every option of this section and its sub-sections, depth first, in declaration order. */
    public void forEachOption(Consumer<Option<?>> action) {
        for (Node child : children) {
            if (child instanceof Option<?> option) {
                action.accept(option);
            } else if (child instanceof Section section) {
                section.forEachOption(action);
            }
        }
    }

    private void setPath(String newPath) {
        this.path = newPath;
        for (Node child : children) {
            String childPath = newPath + "." + child.key();
            if (child instanceof Section section) {
                section.setPath(childPath);
            } else if (child instanceof Option<?> option) {
                option.setPath(childPath);
            }
        }
    }
}
