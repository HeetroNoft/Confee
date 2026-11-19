package fr.heetstudio.confee.core;

import java.util.Map;

/**
 * Element operations of a list shown as a section whose children are its elements, in order (spec 8.2 bis: lists
 * of objects and lists of lists in other mods' files). Each operation first keeps the values being edited, then
 * changes the list; nothing is written to the file before "Save".
 * <p>Internal: may change without notice.</p>
 */
public interface ListActions {

    /**
     * Adds a copy of the last element.
     *
     * @param pending values changed on screen and not saved yet
     * @return false when the change could not be made (nothing changed)
     */
    boolean add(Map<Option<?>, Object> pending);

    boolean remove(int index, Map<Option<?>, Object> pending);

    /** Swaps the element with the next ({@code delta} = 1) or previous ({@code delta} = -1) one. */
    boolean move(int index, int delta, Map<Option<?>, Object> pending);
}
