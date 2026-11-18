package fr.heetstudio.confee.core;

import fr.heetstudio.confee.api.annotation.Name;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversions for lists of objects declared as {@code List<SomeClass>} with {@code SomeClass} annotated
 * {@code @Section}: its public scalar fields are the object fields, a new instance gives their defaults.
 * <p>Internal: may change without notice.</p>
 */
final class ObjectLists {

    private ObjectLists() {
    }

    static List<ObjectField> fields(Class<?> type) {
        Object defaults = newInstance(type);
        List<ObjectField> out = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isTransient(mods) || !Modifier.isPublic(mods) || field.isSynthetic()) {
                continue;
            }
            OptionType optionType = scalarType(field.getType());
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> enumClass = field.getType().isEnum() ? (Class<? extends Enum<?>>) field.getType() : null;
            Object value = read(field, defaults);
            if (value == null) {
                throw new IllegalArgumentException("field " + type.getSimpleName() + "." + field.getName() + " has no initial value");
            }
            Name name = field.getAnnotation(Name.class);
            out.add(new ObjectField(field.getName(), optionType, enumClass, value, name == null ? null : name.value()));
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException(type.getSimpleName() + " has no public field");
        }
        return List.copyOf(out);
    }

    private static OptionType scalarType(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) {
            return OptionType.BOOLEAN;
        }
        if (type == int.class || type == Integer.class) {
            return OptionType.INT;
        }
        if (type == long.class || type == Long.class) {
            return OptionType.LONG;
        }
        if (type == float.class || type == Float.class) {
            return OptionType.FLOAT;
        }
        if (type == double.class || type == Double.class) {
            return OptionType.DOUBLE;
        }
        if (type == String.class) {
            return OptionType.STRING;
        }
        if (type.isEnum()) {
            return OptionType.ENUM;
        }
        throw new IllegalArgumentException("unsupported field type in a list of objects: " + type.getName());
    }

    static List<Object> toMaps(List<?> objects, List<ObjectField> fields) {
        List<Object> out = new ArrayList<>(objects.size());
        for (Object object : objects) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (ObjectField f : fields) {
                try {
                    map.put(f.key(), read(object.getClass().getField(f.key()), object));
                } catch (NoSuchFieldException e) {
                    throw new IllegalStateException(e);
                }
            }
            out.add(Collections.unmodifiableMap(map));
        }
        return Collections.unmodifiableList(out);
    }

    static List<Object> toObjects(List<?> maps, Class<?> type) {
        List<Object> out = new ArrayList<>(maps.size());
        for (Object element : maps) {
            Object object = newInstance(type);
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) element).entrySet()) {
                try {
                    Field field = type.getField(String.valueOf(entry.getKey()));
                    field.setAccessible(true);
                    field.set(object, entry.getValue());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("cannot set " + entry.getKey() + " on " + type.getName(), e);
                }
            }
            out.add(object);
        }
        return out;
    }

    private static Object read(Field field, Object instance) {
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object newInstance(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(type.getName() + " needs a constructor without arguments", e);
        }
    }
}
