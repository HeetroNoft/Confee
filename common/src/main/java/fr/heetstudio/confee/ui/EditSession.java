package fr.heetstudio.confee.ui;

import fr.heetstudio.confee.core.EditableConfig;
import fr.heetstudio.confee.core.Option;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pending edits of a config screen (spec 10.4): changes stay here until "Save".
 * Pure logic, no Minecraft classes.
 * <p>Internal: may change without notice.</p>
 */
public final class EditSession {

    private final EditableConfig handle;
    private final Map<Option<?>, Object> pending = new LinkedHashMap<>();

    public EditSession(EditableConfig handle) {
        this.handle = handle;
    }

    public EditableConfig handle() {
        return handle;
    }

    /** Value shown on screen: the pending one, else the current one. */
    @SuppressWarnings("unchecked")
    public <T> T get(Option<T> option) {
        return pending.containsKey(option) ? (T) pending.get(option) : option.get();
    }

    /** Stores an already validated value. Setting the current value back removes the pending change. */
    public <T> void set(Option<T> option, T value) {
        if (Objects.equals(value, option.get())) {
            pending.remove(option);
        } else {
            pending.put(option, value);
        }
    }

    public boolean isDirty() {
        return !pending.isEmpty() || handle.hasUnsavedStructure();
    }

    /** Values changed on screen and not saved yet. */
    public Map<Option<?>, Object> pendingValues() {
        return java.util.Collections.unmodifiableMap(pending);
    }

    public boolean isChanged(Option<?> option) {
        return pending.containsKey(option);
    }

    /** True when the shown value equals the default (or the default is unknown). */
    public boolean isDefault(Option<?> option) {
        Object def = option.getDefault();
        return def == null || Objects.equals(def, get(option));
    }

    public <T> void resetToDefault(Option<T> option) {
        T def = option.getDefault();
        if (def != null && !option.readOnly()) {
            set(option, def);
        }
    }

    /** "Reset all" (spec 10.4): every option of the file goes back to its default, still pending until saved. */
    public void resetAllToDefault() {
        handle.schema().root().forEachOption(this::resetToDefault);
    }

    public void discard() {
        pending.clear();
        handle.discard();
    }

    /**
     * Applies the pending values in the order of spec 9.6: in-memory values and option listeners,
     * then the config's onChange callbacks, then the file.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Result commit() {
        List<Option<?>> changed = new ArrayList<>(pending.keySet());
        boolean restart = false;
        for (Map.Entry<Option<?>, Object> entry : pending.entrySet()) {
            Option option = entry.getKey();
            // a duplicate typed in a list disappears silently on save; a @Sorted list is sorted
            option.apply(option.normalizeList(entry.getValue()));
            restart |= option.requiresRestart();
        }
        pending.clear();
        if (!changed.isEmpty() || handle.hasUnsavedStructure()) {
            handle.fireOnChange();
            handle.save();
        }
        return new Result(changed.size(), restart);
    }

    /**
     * @param changed         number of options that changed
     * @param restartRequired at least one changed option requires a game restart
     */
    public record Result(int changed, boolean restartRequired) {
    }
}
