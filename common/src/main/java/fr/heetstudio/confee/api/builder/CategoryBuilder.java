package fr.heetstudio.confee.api.builder;

import fr.heetstudio.confee.core.Section;

/**
 * Options of one category (a tab of the sidebar, a table in the file). Obtained from
 * {@link SectionBuilder#category(String)}. Categories can be nested.
 */
public final class CategoryBuilder extends SectionBuilder {

    CategoryBuilder(ConfigBuilder owner, Section section) {
        super(owner, section);
    }

    /** Icon of the category: an item ID ({@code minecraft:feather}) or a texture ({@code examplemod:textures/gui/dash.png}). */
    public CategoryBuilder icon(String icon) {
        owner.checkNotBuilt();
        section.setIcon(icon);
        return this;
    }

    /** Comment written above the category's table in the file. */
    public CategoryBuilder comment(String comment) {
        owner.checkNotBuilt();
        section.setComment(comment);
        return this;
    }
}
