package cn.dawnstring.circe.api;

import cn.dawnstring.circe.quest.QuestValidationException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record QuestConfigSchema(List<Field> fields)
{
    public static final QuestConfigSchema EMPTY = new QuestConfigSchema(List.of());

    public enum Kind
    {
        TEXT, INTEGER, DECIMAL, BOOLEAN, RESOURCE, ITEM, BLOCK, ENTITY, FLUID, CHOICE
    }

    public record Option(String id, String title)
    {
        public Option
        {
            if (id == null || id.isBlank() || title == null || title.isBlank() || id.length() > 128 || title.length() > 128)
            {
                throw new IllegalArgumentException("circe.validation.config_option");
            }
        }
    }

    public record Field(String key, String title, Kind kind, JsonElement defaultValue,
        double minimum, double maximum, int maximumLength, String unit, List<Option> options)
    {
        public Field
        {
            if (key == null || !key.matches("[a-z0-9_]{1,64}") || title == null || title.isBlank() || title.length() > 128
                || !Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum
                || maximumLength < 1 || maximumLength > 2048 || unit == null || unit.length() > 32)
            {
                throw new IllegalArgumentException("circe.validation.config_field");
            }
            Objects.requireNonNull(kind);
            defaultValue = Objects.requireNonNull(defaultValue).deepCopy();
            options = List.copyOf(options);
            if (options.size() > 64 || options.stream().map(Option::id).distinct().count() != options.size())
            {
                throw new IllegalArgumentException("circe.validation.config_options");
            }
        }

        @Override
        public JsonElement defaultValue()
        {
            return defaultValue.deepCopy();
        }

        public static Field text(String key, String title, String value, int maximumLength)
        {
            return new Field(key, title, Kind.TEXT, new JsonPrimitive(value), 0, 0, maximumLength, "", List.of());
        }

        public static Field number(String key, String title, Kind kind, double value, double minimum, double maximum, String unit)
        {
            if (kind != Kind.INTEGER && kind != Kind.DECIMAL)
            {
                throw new IllegalArgumentException("circe.validation.config_number_kind");
            }
            return new Field(key, title, kind, new JsonPrimitive(value), minimum, maximum, 64, unit, List.of());
        }

        public static Field bool(String key, String title, boolean value)
        {
            return new Field(key, title, Kind.BOOLEAN, new JsonPrimitive(value), 0, 0, 5, "", List.of());
        }

        public static Field resource(String key, String title, Kind kind, ResourceLocation value)
        {
            if (!List.of(Kind.RESOURCE, Kind.ITEM, Kind.BLOCK, Kind.ENTITY, Kind.FLUID).contains(kind))
            {
                throw new IllegalArgumentException("circe.validation.config_resource_kind");
            }
            return new Field(key, title, kind, new JsonPrimitive(value.toString()), 0, 0, 256, "", List.of());
        }

        public static Field choice(String key, String title, String value, List<Option> options)
        {
            return new Field(key, title, Kind.CHOICE, new JsonPrimitive(value), 0, 0, 128, "", options);
        }

        public JsonElement validate(JsonElement value)
        {
            if (!value.isJsonPrimitive())
            {
                throw new QuestValidationException("circe.validation.field_scalar", Component.translatable(title));
            }
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (kind == Kind.BOOLEAN)
            {
                if (!primitive.isBoolean())
                {
                    throw new QuestValidationException("circe.validation.field_boolean", Component.translatable(title));
                }
                return primitive.deepCopy();
            }
            if (kind == Kind.INTEGER || kind == Kind.DECIMAL)
            {
                if (!primitive.isNumber())
                {
                    throw new QuestValidationException("circe.validation.field_number", Component.translatable(title));
                }
                BigDecimal number = primitive.getAsBigDecimal();
                if (number.compareTo(BigDecimal.valueOf(minimum)) < 0 || number.compareTo(BigDecimal.valueOf(maximum)) > 0
                    || kind == Kind.INTEGER && number.stripTrailingZeros().scale() > 0)
                {
                    throw new QuestValidationException("circe.validation.field_range", Component.translatable(title), minimum, maximum);
                }
                return new JsonPrimitive(number.stripTrailingZeros());
            }
            if (!primitive.isString() || primitive.getAsString().length() > maximumLength)
            {
                throw new QuestValidationException("circe.validation.field_text", Component.translatable(title));
            }
            String text = primitive.getAsString();
            if (kind == Kind.CHOICE && options.stream().noneMatch(option -> option.id().equals(text)))
            {
                throw new QuestValidationException("circe.validation.field_option", Component.translatable(title));
            }
            if (List.of(Kind.RESOURCE, Kind.ITEM, Kind.BLOCK, Kind.ENTITY, Kind.FLUID).contains(kind))
            {
                ResourceLocation id = ResourceLocation.parse(text);
                boolean isRegistered = switch (kind)
                {
                    case ITEM -> BuiltInRegistries.ITEM.containsKey(id);
                    case BLOCK -> BuiltInRegistries.BLOCK.containsKey(id);
                    case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
                    case FLUID -> BuiltInRegistries.FLUID.containsKey(id);
                    default -> true;
                };
                if (!isRegistered)
                {
                    throw new QuestValidationException("circe.validation.field_resource", Component.translatable(title), id);
                }
                return new JsonPrimitive(id.toString());
            }
            return primitive.deepCopy();
        }
    }

    public QuestConfigSchema
    {
        fields = List.copyOf(fields);
        var keys = new HashSet<String>();
        if (fields.size() > 16 || fields.stream().anyMatch(field -> !keys.add(field.key())))
        {
            throw new IllegalArgumentException("circe.validation.config_fields");
        }
    }

    public JsonObject normalize(JsonObject configuration)
    {
        if (configuration.toString().length() > 8192)
        {
            throw new IllegalArgumentException("circe.validation.config_size");
        }
        if (configuration.keySet().stream().anyMatch(key -> fields.stream().noneMatch(field -> field.key().equals(key))))
        {
            throw new IllegalArgumentException("circe.validation.config_undeclared");
        }
        JsonObject normalized = new JsonObject();
        for (Field field : fields)
        {
            normalized.add(field.key(), field.validate(configuration.has(field.key())
                ? configuration.get(field.key()) : field.defaultValue()));
        }
        if (normalized.toString().length() > 8192)
        {
            throw new IllegalArgumentException("circe.validation.config_defaults");
        }
        return normalized;
    }
}
