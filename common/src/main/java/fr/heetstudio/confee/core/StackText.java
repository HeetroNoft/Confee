package fr.heetstudio.confee.core;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Item stack written as SNBT ({@code @ItemStack}): item ID, count, extra data. Reads the forms of every version
 * ({@code Count}/{@code tag} up to 1.20.4, {@code count}/{@code components} from 1.20.5, or a plain item ID) and
 * writes the form of this version. Server safe: it does not look the item up.
 * <p>Internal: may change without notice.</p>
 *
 * @param id    item ID, e.g. {@code minecraft:diamond}
 * @param count count, at least 1
 * @param extra extra data as an SNBT compound ({@code {Damage:3}}), or null
 */
public record StackText(String id, int count, @Nullable String extra) {

    /** Key of the extra data written by this version. */
    public static final String EXTRA_KEY = "tag";

    /**
     * Reads a stack.
     *
     * @throws IllegalArgumentException with a message for the player when the text is not a stack
     */
    public static StackText parse(String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("empty item stack");
        }
        if (!t.startsWith("{")) {
            return new StackText(checkId(t), 1, null);
        }
        CompoundTag tag;
        try {
            tag = TagParser.parseTag(t);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("invalid SNBT: " + e.getMessage());
        }
        if (!tag.contains("id", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("no item id");
        }
        String id = checkId(tag.getString("id"));
        int count = 1;
        if (tag.contains("Count", Tag.TAG_ANY_NUMERIC)) {
            count = tag.getInt("Count");
        } else if (tag.contains("count", Tag.TAG_ANY_NUMERIC)) {
            count = tag.getInt("count");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count below 1");
        }
        CompoundTag extra = tag.contains("tag", Tag.TAG_COMPOUND) ? tag.getCompound("tag")
                : tag.contains("components", Tag.TAG_COMPOUND) ? tag.getCompound("components") : null;
        return new StackText(id, count, extra == null || extra.isEmpty() ? null : extra.toString());
    }

    /** Checks the extra data text: null when it is empty or a valid SNBT compound, else the problem. */
    @Nullable
    public static String extraProblem(String extra) {
        String t = extra == null ? "" : extra.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            TagParser.parseTag(t);
            return null;
        } catch (CommandSyntaxException e) {
            return e.getMessage();
        }
    }

    private static String checkId(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null) {
            throw new IllegalArgumentException("'" + id + "' is not an item ID");
        }
        return rl.toString();
    }

    /** SNBT in the form of this version, e.g. {@code {id:"minecraft:diamond",Count:3b,tag:{Damage:3}}}. */
    public String write() {
        StringBuilder out = new StringBuilder("{id:").append(StringTag.quoteAndEscape(id)).append(",Count:").append(count).append('b');
        if (extra != null && !extra.isBlank()) {
            out.append(',').append(EXTRA_KEY).append(':').append(extra.trim());
        }
        return out.append('}').toString();
    }

    public StackText withId(String newId) {
        return new StackText(newId, count, extra);
    }

    public StackText withCount(int newCount) {
        return new StackText(id, newCount, extra);
    }

    public StackText withExtra(@Nullable String newExtra) {
        return new StackText(id, count, newExtra == null || newExtra.isBlank() ? null : newExtra.trim());
    }
}
