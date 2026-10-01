package it.unibz.inf.pm.ocel.entity;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.time.OffsetTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An element is composed of a key and value(s). The key is string-based,
 * whereas the value may be string, timestamp, integer, ﬂoat, boolean or a list of strings.
 */
public class OcelElement implements Serializable {

    @Serial
    private static final long serialVersionUID = 2369639688628591130L;

    Logger log = LoggerFactory.getLogger(OcelElement.class);

    public enum valueType {
        OTHER("Other"),
        STRING("String"),
        INTEGER("Integer"), FLOAT("Float"), 
        BOOLEAN("Boolean"),
        DATE("Date"),
        DATETIME("DateTime"), ZONED_DATE_TIME("ZonedDateTime"),
        ZONED_TIME("ZonedTime"), LOCALTIME("LocalTime");

        private final String code;

        valueType(String other) {
            this.code = other;
        }

        public static @NonNull valueType tryMatchAgainst(String typeString) {
            for (valueType type : valueType.values()) {
                if (type.code.equals(typeString)) {
                    return type;
                }
            }
            return OTHER;
        }
    }

    public static List<String> types() {
        return Arrays.stream(valueType.values()).map(v -> v.code).sorted().collect(Collectors.toList());
    }

    @Getter
    @NonNull
    @JsonProperty("ocel:attribute-type")
    private valueType type;

    @Getter
    @JsonProperty("ocel:attribute-value")
    private Object value;
    @Getter
    @JsonProperty("ocel:attribute-list-value")
    private Collection<String> listValue;

    @Getter
    private String rawValue;

//    public OcelElement(Object value) {
//        if (Objects.requireNonNull(value) instanceof Number number) {
//            new OcelElement(number);
//        } else if (value instanceof Boolean b) {
//            new OcelElement(b);
//        } else if (value instanceof Temporal temporal) {
//            new OcelElement(temporal);
//        } else if (value instanceof String s) {
//            new OcelElement(s, true);
//        } else {
//            log.warn("{} is of type {}, not a string, numeric, temporal, boolean, collection-of-string or " +
//                            "datetime-like value", value.toString(),
//                    value.getClass());
//            new OcelElement(value.toString(), false);
//        }
//    }


    public OcelElement(Object value) {
        this.rawValue = value.toString();
        this.value = value;
        this.type = valueType.OTHER;
    }

    public OcelElement(String value, String typeString) {
        this.rawValue = value;
        this.type = valueType.tryMatchAgainst(typeString);
        try {
            switch (this.type) {
                case INTEGER -> this.value = Integer.parseInt(value);
                case FLOAT -> {
                    this.value = Float.parseFloat(value);
                }
                case STRING -> {
                    this.value = value;
                }
                case BOOLEAN -> {
                    this.value = Boolean.parseBoolean(value);
                }
                case DATE, DATETIME -> this.value = LocalDateTime.parse(value);
                case LOCALTIME -> this.value = LocalTime.parse(value);
                case ZONED_DATE_TIME -> this.value = ZonedDateTime.parse(value);
                case ZONED_TIME -> this.value = OffsetTime.parse(value);
                case OTHER -> this.value = value;
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            this.value = value;
        }
    }
}
