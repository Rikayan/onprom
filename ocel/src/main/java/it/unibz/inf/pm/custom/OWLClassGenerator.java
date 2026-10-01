
package it.unibz.inf.pm.custom;

import com.alibaba.fastjson.util.ParameterizedTypeImpl;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OWLClassGenerator {

    private static final ConcurrentHashMap<String, Class<?>> generatedClasses = new ConcurrentHashMap<>();
    private static final Map<String, OWLClass> usedOWLClasses = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Set<OWLClass>> topLevelClassesGenerated = new ConcurrentHashMap<>();
    private static final Map<OWLClass, Set<OWLObjectProperty>> objectPropertiesCache = new ConcurrentHashMap<>();
    private static final Map<OWLClass, Set<OWLDataProperty>> dataPropertiesCache = new ConcurrentHashMap<>();

    public static OWLClass getOWLClass(String className) {
        return usedOWLClasses.get(className);
    }

    public static OWLObjectProperty getObjectProperty(String className, String propertyName) {
        List<OWLObjectProperty> properties = objectPropertiesCache.get(getOWLClass(className)).stream()
                .filter(owlObjectProperty ->
                        sanitizeIdentifier(owlObjectProperty.getIRI().getShortForm()).equals(propertyName)).toList();
        if(properties.size() == 1) {
            return properties.getFirst();
        }
        throw new RuntimeException("Non-unique object property found for class: " + className +
                " and property: " + propertyName);
    }

    public static OWLDataProperty getDataProperty(String className, String propertyName) {
        List<OWLDataProperty> properties = dataPropertiesCache.get(getOWLClass(className)).stream()
                .filter(owlDataProperty ->
                        sanitizeIdentifier(owlDataProperty.getIRI().getShortForm()).equals(propertyName)).toList();
        if(properties.size() == 1) {
            return properties.getFirst();
        }
        throw new RuntimeException("Non-unique data property found for class: " + className +
                " and property: " + propertyName);
    }

    public record ClassMetadata<T>(Class<T> aClass, Set<OWLClass> topLevelClasses) {}

    private static final Map<Class<?>, Map<String, Type>> fieldTypeCache = new ConcurrentHashMap<>();

    public static ClassMetadata<?> generateMainClass(OWLOntology ontology) {
        String shortForm = ontology.getOntologyID().getOntologyIRI()
                .orElseGet(() -> IRI.create("unknownSchema"))
                .getShortForm();

        if (generatedClasses.containsKey(shortForm)) {
            Class<?> aClass = generatedClasses.get(shortForm);
            return new ClassMetadata<>(aClass, topLevelClassesGenerated.get(shortForm));
        }

        // Cache all properties for efficient lookup
        cacheObjectProperties(ontology);
        cacheDataProperties(ontology);

        // Generate classes for all entities in the ontology
        for (OWLClass klass : ontology.getClassesInSignature()) {
            generateClassFromOntology(klass.getIRI().getShortForm(), klass, ontology);
        }

        // Find top-level classes (those with no incoming object properties)
        Set<OWLClass> topLevelClasses = findTopLevelClasses(ontology);


        // Build the main Log class with List fields for each top-level class
        DynamicType.Builder<?> builder = new ByteBuddy()
                .subclass(Object.class)
                .name("generated." + sanitizeIdentifier(shortForm) + "Log");

        Map<String, Type> fieldTypes = new HashMap<>();
        for (OWLClass topLevelClass : topLevelClasses) {
            String className = sanitizeIdentifier(topLevelClass.getIRI().getShortForm());
            Class<?> topLevelJavaClass = generatedClasses.get(className);

            if (topLevelJavaClass != null) {
                String fieldName = className + "s";  // plural form
                TypeDescription.Generic listType = TypeDescription.Generic.Builder
                        .parameterizedType(List.class, topLevelJavaClass)
                        .build();
                fieldTypes.put(fieldName, new ParameterizedTypeImpl(List.class, topLevelJavaClass));
                builder = builder.defineField(fieldName, listType, Visibility.PRIVATE);
            }
        }

        try (DynamicType.Unloaded<?> unloaded = builder.make()) {
            Class<?> generatedLogClass = unloaded.load(OWLClassGenerator.class.getClassLoader()).getLoaded();
            fieldTypeCache.put(generatedLogClass, fieldTypes);
            topLevelClassesGenerated.put(shortForm, topLevelClasses);
            generatedClasses.put(sanitizeIdentifier(shortForm) + "Log", generatedLogClass);
            return new ClassMetadata<>(generatedLogClass, topLevelClasses);
        }
    }

    // Add helper class for ParameterizedType
    private static class ParameterizedTypeImpl implements ParameterizedType {
        private final Type rawType;
        private final Type[] typeArgs;

        ParameterizedTypeImpl(Type rawType, Type... typeArgs) {
            this.rawType = rawType;
            this.typeArgs = typeArgs;
        }

        @Override
        public Type[] getActualTypeArguments() { return typeArgs; }
        @Override
        public Type getRawType() { return rawType; }
        @Override
        public Type getOwnerType() { return null; }
    }

    public static Type getFieldGenericType(Class<?> klass, String fieldName) {
        Map<String, Type> types = fieldTypeCache.get(klass);
        return types != null ? types.get(fieldName) : null;
    }

    /**
     * Find top-level classes using Tarjan's SCC algorithm
     * A top-level class is one with indegree 0 in the object property dependency graph
     */
    private static Set<OWLClass> findTopLevelClasses(OWLOntology ontology) {
        Set<OWLClass> allClasses = ontology.getClassesInSignature();

        // Build indegree map: count incoming edges for each class
        Map<OWLClass, Integer> indegree = new HashMap<>();
        allClasses.forEach(c -> indegree.put(c, 0));

        // For each object property, increment indegree of range class
        ontology.getObjectPropertiesInSignature().forEach(prop ->
                ontology.getObjectPropertyRangeAxioms(prop).forEach(rangeAxiom -> {
                    OWLClass range = rangeAxiom.getRange().asOWLClass();
                    indegree.merge(range, 1, Integer::sum);
                })
        );

        // Top-level classes are those with indegree 0
        Set<OWLClass> topLevel = indegree.entrySet().stream()
                .filter(e -> e.getValue() == 0)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // Never include owl:Thing
        topLevel.removeIf(c -> c.isOWLThing());

        return topLevel;
    }

    /**
     * Cache object properties by their domain classes
     */
    private static void cacheObjectProperties(OWLOntology ontology) {
        objectPropertiesCache.clear();
        ontology.getObjectPropertiesInSignature().forEach(owlObjectProperty ->
                ontology.getObjectPropertyDomainAxioms(owlObjectProperty).forEach(domainAxiom -> {
                    OWLClass domain = domainAxiom.getDomain().asOWLClass();
                    if (!domain.isOWLThing()) {
                        objectPropertiesCache.computeIfAbsent(domain, k -> new HashSet<>())
                                .add(owlObjectProperty);
                    }
                })
        );
    }

    /**
     * Cache data properties by their domain classes
     */
    private static void cacheDataProperties(OWLOntology ontology) {
        dataPropertiesCache.clear();
        ontology.getDataPropertiesInSignature().forEach(owlDataProperty ->
                ontology.getDataPropertyDomainAxioms(owlDataProperty).forEach(domainAxiom -> {
                    OWLClass domain = domainAxiom.getDomain().asOWLClass();
                    if (!domain.isOWLThing()) {
                        dataPropertiesCache.computeIfAbsent(domain, k -> new HashSet<>())
                                .add(owlDataProperty);
                    }
                })
        );
    }

    private static Class<?> getJavaTypeFromOWLDatatype(OWLDataProperty prop, OWLOntology ontology) {
        Set<Class<?>> dataTypes = new HashSet<>();

        ontology.axioms(prop)
                .filter(axiom -> axiom.isOfType(AxiomType.DATA_PROPERTY_RANGE))
                .forEach(axiom -> {
                    OWLDataPropertyRangeAxiom rangeAxiom = (OWLDataPropertyRangeAxiom) axiom;
                    OWLDataRange dataRange = rangeAxiom.getRange();

                    if (dataRange.isOWLDatatype()) {
                        OWL2Datatype builtInDatatype = dataRange.asOWLDatatype().getBuiltInDatatype();

                        switch (builtInDatatype) {
                            case XSD_STRING:
                            case XSD_ANY_URI:
                                dataTypes.add(String.class);
                                break;
                            case XSD_INTEGER:
                            case XSD_INT:
                                dataTypes.add(Integer.class);
                                break;
                            case XSD_FLOAT:
                                dataTypes.add(Float.class);
                                break;
                            case XSD_BOOLEAN:
                                dataTypes.add(Boolean.class);
                                break;
                            case XSD_DATE_TIME:
                            case XSD_DATE_TIME_STAMP:
                                dataTypes.add(Instant.class);
                                break;
                            case XSD_DECIMAL:
                                dataTypes.add(Double.class);
                                break;
                            case XSD_SHORT:
                            case XSD_UNSIGNED_BYTE:
                                dataTypes.add(Short.class);
                                break;
                            case XSD_LONG:
                                dataTypes.add(Long.class);
                                break;
                            case XSD_BYTE:
                                dataTypes.add(Byte.class);
                                break;
                            default:
                                throw new RuntimeException("Unsupported datatype: " + builtInDatatype);
                        }
                    }
                });

        if (dataTypes.size() == 1) {
            return dataTypes.iterator().next();
        } else if (dataTypes.isEmpty()) {
            throw new RuntimeException("No datatype found for property: " + prop.getIRI());
        } else {
            throw new RuntimeException("Multiple datatypes found for property: " + prop.getIRI());
        }
    }

    public static Class<?> generateClassFromOntology(String shortForm, OWLClass owlClass, OWLOntology ontology) {
        String className = sanitizeIdentifier(shortForm);
        usedOWLClasses.put(className, owlClass);

        if (generatedClasses.containsKey(className)) {
            return generatedClasses.get(className);
        }

        DynamicType.Builder<?> builder = new ByteBuddy()
                .subclass(Object.class)
                .name("generated." + className);

        // Add data properties specific to this class
        if (owlClass != null && dataPropertiesCache.containsKey(owlClass)) {
            for (OWLDataProperty prop : dataPropertiesCache.get(owlClass)) {
                String propName = prop.getIRI().getShortForm();
                Class<?> propType = getJavaTypeFromOWLDatatype(prop, ontology);
                builder = builder.defineField(propName, propType, Visibility.PRIVATE);
            }
        }

        // Add object properties specific to this class
        Set<OWLObjectProperty> objectProps = owlClass != null && objectPropertiesCache.containsKey(owlClass)
                ? objectPropertiesCache.get(owlClass)
                : new HashSet<>();

        for (OWLObjectProperty prop : objectProps) {
            String propName = prop.getIRI().getShortForm();
            OWLClass targetClass = getTargetClass(prop, ontology);

            Class<?> targetClassType;
            String targetClassName = sanitizeIdentifier(targetClass.getIRI().getShortForm());

            if (generatedClasses.containsKey(targetClassName)) {
                targetClassType = generatedClasses.get(targetClassName);
            } else {
                targetClassType = generateClassFromOntology(targetClassName, targetClass, ontology);
            }

            builder = builder.defineField(propName, targetClassType, Visibility.PRIVATE);
        }

        try (DynamicType.Unloaded<?> unloaded = builder.make()) {
            Class<?> generatedClass = unloaded.load(OWLClassGenerator.class.getClassLoader()).getLoaded();
            generatedClasses.put(className, generatedClass);
            return generatedClass;
        }
    }

    private static OWLClass getTargetClass(OWLObjectProperty prop, OWLOntology ontology) {
        Set<OWLClass> targetClasses = new HashSet<>();

        ontology.axioms(prop)
                .filter(axiom -> axiom.isOfType(AxiomType.OBJECT_PROPERTY_RANGE))
                .forEach(axiom -> axiom.classesInSignature()
                        .filter(c -> !c.isOWLThing())
                        .forEach(targetClasses::add)
                );

        if (targetClasses.size() == 1) {
            return targetClasses.iterator().next();
        } else if (targetClasses.isEmpty()) {
            throw new RuntimeException("No target class found for property: " + prop.getIRI());
        } else {
            throw new RuntimeException("Multiple target classes found for property: " + prop.getIRI());
        }
    }

    private static @NonNull String sanitizeIdentifier(String shortForm) {
        String className = shortForm.replaceAll("[^a-zA-Z0-9_$]", "_");

        if (className.isEmpty() || !Character.isJavaIdentifierStart(className.charAt(0))) {
            className = "_" + className;
        }
        return className;
    }

    @SuppressWarnings("unchecked")
    public static <T> T parseValueFromString(OWLLiteral x, Class<T> type) {
        if (type.equals(String.class)) {
            return (T) x.getLiteral();
        } else if (type.equals(Integer.class)) {
            return (T) (Integer) x.parseInteger();
        } else if (type.equals(Float.class)) {
            return (T) (Float) x.parseFloat();
        } else if (type.equals(Boolean.class)) {
            return (T) (Boolean) x.parseBoolean();
        } else if (type.equals(Instant.class)) {
            return (T) Instant.parse(x.getLiteral());
        } else if (type.equals(Double.class)) {
            return (T) (Double) x.parseDouble();
        } else if (type.equals(Short.class)) {
            return (T) (Short) Short.parseShort(x.getLiteral());
        } else if (type.equals(Byte.class)) {
            return (T) (Byte) Byte.parseByte(x.getLiteral());
        } else if (type.equals(Long.class)) {
            return (T) (Long) Long.parseLong(x.getLiteral());
        }
        throw new RuntimeException("Unsupported type: " + type.getName());
    }

    public static <T> boolean isDynamicClass(Class<T> targetType) {
        return targetType.getName().startsWith("generated.");
    }
}