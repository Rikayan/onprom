package it.unibz.inf.onprom.cli;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.Version;
import tools.jackson.databind.*;
import tools.jackson.databind.ser.Serializers;

import java.lang.reflect.Field;

public class DynamicClassModule extends JacksonModule {

    @Override
    public String getModuleName() {
        return "DynamicClassModule";
    }

    @Override
    public Version version() {
        return new Version(1, 0, 0, null, null, null);
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addSerializers(new Serializers.Base() {

            @Override
            public ValueSerializer<?> findSerializer(SerializationConfig config, JavaType type,
                                                     BeanDescription.Supplier beanDescRef,
                                                     JsonFormat.Value formatOverrides) {
                if (type.getRawClass().getName().startsWith("generated.")) {
                    return new DynamicClassSerializer();
                }
                return super.findSerializer(config, type, beanDescRef, formatOverrides);
            }
        });
    }

    /**
     * Serializer for ByteBuddy-generated dynamic classes
     */
    private static class DynamicClassSerializer extends ValueSerializer<Object> {

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext context) {
            if (value == null) {
                gen.writeNull();
                return;
            }

            gen.writeStartObject(value);

            try {
                for (Field field : value.getClass().getDeclaredFields()) {
                    // Skip synthetic/compiler-generated fields (e.g., $jacocoData, $SWITCH_TABLE$)
                    if (field.isSynthetic()) {
                        continue;
                    }

                    field.setAccessible(true);
                    Object fieldValue = field.get(value);

                    // 2. Write key-value pairs inside the object
                    context.defaultSerializeProperty(field.getName(), fieldValue, gen);
                }
            } catch (IllegalAccessException | TypeNotPresentException e) {
                // Skip problematic fields
            }

            // 3. Close the object block
            gen.writeEndObject();
        }
    }
}