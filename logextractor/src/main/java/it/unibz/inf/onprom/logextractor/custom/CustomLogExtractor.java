package it.unibz.inf.onprom.logextractor.custom;

import it.unibz.inf.onprom.obdamapper.utility.OntopUtility;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.owlapi.OntopOWLFactory;
import it.unibz.inf.ontop.owlapi.OntopOWLReasoner;
import it.unibz.inf.ontop.owlapi.connection.OntopOWLStatement;
import it.unibz.inf.ontop.owlapi.resultset.OWLBindingSet;
import it.unibz.inf.ontop.owlapi.resultset.TupleOWLResultSet;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import it.unibz.inf.pm.custom.OWLClassGenerator;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.*;

/**
 * Extracts log data from a VKG into dynamically generated classes with top-level object collections.
 */
public class CustomLogExtractor {
    private static final Logger logger = LoggerFactory.getLogger(CustomLogExtractor.class);

    private final OWLOntology ontology;
    private final Map<String, Object> objectCache = new HashMap<>();  // Cache for nested object instantiation

    public CustomLogExtractor(OWLOntology ontology) {
        this.ontology = ontology;
    }

    /**
     * Extract log from VKG and populate top-level object lists
     */
    public <T> T extractLog(SQLPPMapping ebdaModel, Properties dataSourceProperties,
                            OWLClassGenerator.@MonotonicNonNull ClassMetadata<T> logClassAndTop) throws Exception {
        Class<T> logClass = logClassAndTop.aClass();
        try {
            // 1. Instantiate the log container
            T logInstance = logClass.getDeclaredConstructor().newInstance();
            logger.info("Instantiated log: {}", logClass.getSimpleName());

            // 2. Extract metadata about the schema
            PropertyMetadata metadata = new PropertyMetadata(logClass);

            // 3. For each top-level class field (List<TopLevelClass>), populate it
            for (Field field : logClass.getDeclaredFields()) {
                field.setAccessible(true);

                // Check if field is a List type
                if (List.class.isAssignableFrom(field.getType())) {
                    Class<?> elementType = extractListElementType(field);
                    if (elementType != null && OWLClassGenerator.isDynamicClass(elementType)) {
                        List<Object> topLevelObjects = extractTopLevelObjects(elementType, ebdaModel,
                                dataSourceProperties, metadata);
                        field.set(logInstance, topLevelObjects);
                        logger.info("Populated {}: {} instances", field.getName(), topLevelObjects.size());
                    }
                }
            }

            logger.info("Successfully extracted log with {} fields", logClass.getDeclaredFields().length);
            return logInstance;

        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Generated class must have a no-arg constructor: " + logClass.getName(), e);
        } finally {
            objectCache.clear();
        }
    }

    /**
     * Extract all top-level instances of a class from the VKG
     */
    private List<Object> extractTopLevelObjects(Class<?> elementType, SQLPPMapping ebdaModel,
                                                Properties dataSourceProperties, PropertyMetadata metadata)
            throws Exception {

        List<Object> instances = new ArrayList<>();

        // Query for all instances of this class type
        String sparqlQuery = buildClassInstanceQuery(elementType.getSimpleName());
        logger.debug("Querying top-level objects: {}", sparqlQuery);

        OntopSQLOWLAPIConfiguration config = OntopUtility.getConfiguration(ebdaModel, dataSourceProperties);
        try (OntopOWLReasoner reasoner = OntopOWLFactory.defaultFactory().createReasoner(ontology, config)) {
            try (OntopOWLStatement stmt = reasoner.getConnection().createStatement()) {
                TupleOWLResultSet results = stmt.executeSelectQuery(sparqlQuery);

                while (results.hasNext()) {
                    OWLBindingSet binding = results.next();
                    String objectKey = binding.getOWLObject(binding.getBindingNames().iterator().next()).toString();

                    // Create or reuse instance
                    Object instance;
                    if (objectCache.containsKey(objectKey)) {
                        instance = objectCache.get(objectKey);
                    } else {
                        instance = elementType.getDeclaredConstructor().newInstance();
                        objectCache.put(objectKey, instance);

                        // Recursively populate nested properties
                        populateNestedProperties(instance, elementType, ebdaModel,
                                dataSourceProperties, metadata);
                    }
                    instances.add(instance);
                }
            }
        }

        return instances;
    }

    /**
     * Recursively populate data and object properties of an instance
     */
    private void populateNestedProperties(Object instance, Class<?> instanceType, SQLPPMapping ebdaModel,
                                          Properties dataSourceProperties, PropertyMetadata metadata)
            throws Exception {

        for (Field field : instanceType.getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName = field.getName();

            if (metadata.isDataProperty(instanceType, fieldName)) {
                // Populate data property
                populateDataProperty(instance, field, ebdaModel, dataSourceProperties);
            } else if (metadata.isObjectProperty(instanceType, fieldName)) {
                // Populate object property (nested object)
                populateObjectProperty(instance, field, ebdaModel, dataSourceProperties, metadata);
            }
        }
    }

    /**
     * Populate a single data property field
     */
    private void populateDataProperty(Object instance, Field field, SQLPPMapping ebdaModel,
                                      Properties dataSourceProperties) throws Exception {

        String sparqlQuery = buildDataPropertyQuery(field.getName());

        OntopSQLOWLAPIConfiguration config = OntopUtility.getConfiguration(ebdaModel, dataSourceProperties);
        try (OntopOWLReasoner reasoner = OntopOWLFactory.defaultFactory().createReasoner(ontology, config)) {
            try (OntopOWLStatement stmt = reasoner.getConnection().createStatement()) {
                TupleOWLResultSet results = stmt.executeSelectQuery(sparqlQuery);

                if (results.hasNext()) {
                    OWLBindingSet binding = results.next();
                    Object value = mapOWLToJava(binding, field.getType());
                    if (value != null) {
                        field.set(instance, value);
                    }
                }
            }
        }
    }

    /**
     * Populate a single object property field (nested object)
     */
    private void populateObjectProperty(Object instance, Field field, SQLPPMapping ebdaModel,
                                        Properties dataSourceProperties, PropertyMetadata metadata)
            throws Exception {

        Class<?> fieldType = field.getType();
        String sparqlQuery = buildObjectPropertyQuery(field.getName());

        OntopSQLOWLAPIConfiguration config = OntopUtility.getConfiguration(ebdaModel, dataSourceProperties);
        try (OntopOWLReasoner reasoner = OntopOWLFactory.defaultFactory().createReasoner(ontology, config)) {
            try (OntopOWLStatement stmt = reasoner.getConnection().createStatement()) {
                TupleOWLResultSet results = stmt.executeSelectQuery(sparqlQuery);

                if (results.hasNext()) {
                    OWLBindingSet binding = results.next();
                    String objectKey = binding.getOWLObject(binding.getBindingNames().iterator().next()).toString();

                    Object nestedInstance;
                    if (objectCache.containsKey(objectKey)) {
                        nestedInstance = objectCache.get(objectKey);
                    } else {
                        nestedInstance = fieldType.getDeclaredConstructor().newInstance();
                        objectCache.put(objectKey, nestedInstance);
                        // Recursively populate nested object's properties
                        populateNestedProperties(nestedInstance, fieldType, ebdaModel,
                                dataSourceProperties, metadata);
                    }
                    field.set(instance, nestedInstance);
                }
            }
        }
    }

    private Class<?> extractListElementType(Field field) {
        try {
            Type cachedType = OWLClassGenerator.getFieldGenericType(field.getDeclaringClass(), field.getName());
            if (cachedType instanceof ParameterizedType) {
                Type[] typeArgs = ((ParameterizedType) cachedType).getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    return (Class<?>) typeArgs[0];
                }
            }

            // Fallback for regular classes
            Type genericType = field.getGenericType();
            if (genericType instanceof ParameterizedType) {
                Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class) {
                    return (Class<?>) typeArgs[0];
                }
            }
        } catch (TypeNotPresentException e) {
            logger.warn("Could not resolve generic type for field {}", field.getName());
        }
        return null;
    }

    private String buildClassInstanceQuery(String className) {
        return String.format("SELECT ?instance WHERE { ?instance rdf:type ex:%s }", className);
    }

    private String buildDataPropertyQuery(String propertyName) {
        return String.format("SELECT ?value WHERE { ?instance ex:%s ?value }", propertyName);
    }

    private String buildObjectPropertyQuery(String propertyName) {
        return String.format("SELECT ?obj WHERE { ?instance ex:%s ?obj }", propertyName);
    }

    @SuppressWarnings("unchecked")
    private <T> T mapOWLToJava(OWLBindingSet binding, Class<T> targetType) throws Exception {
        if (OWLClassGenerator.isDynamicClass(targetType)) {
            String objectKey = binding.getOWLObject(binding.getBindingNames().iterator().next()).toString();
            return (T) objectCache.get(objectKey);
        } else {
            return binding.getOWLLiteral(binding.getBindingNames().iterator().next())
                    .mapLiteral(x -> OWLClassGenerator.parseValueFromString(x, targetType))
                    .orElse(null);
        }
    }

    public static boolean isDynamicClass(Class<?> clazz) {
        ProtectionDomain domain = clazz.getProtectionDomain();
        if (domain == null) return true;

        CodeSource codeSource = domain.getCodeSource();
        return codeSource == null || codeSource.getLocation() == null;
    }

    /**
     * Metadata about the ontology schema
     */
    private static class PropertyMetadata {
        private final Map<Class<?>, Set<String>> classDataProperties = new HashMap<>();
        private final Map<Class<?>, Set<String>> classObjectProperties = new HashMap<>();

        PropertyMetadata(Class<?> logClass) {
            addFields(logClass);

            logger.info("Ontology schema contains {} data properties and {} object properties",
                    classDataProperties.size(), classObjectProperties.size());
        }

        private void addFields(Class<?> klass) {
            for(Field field: klass.getDeclaredFields()) {
                if (isDynamicClass(field.getType())) {
                    classObjectProperties.computeIfAbsent(klass, k -> new HashSet<>()).add(field.getName());
                    addFields(field.getType());
                } else {
                    classDataProperties.computeIfAbsent(klass, k -> new HashSet<>()).add(field.getName());
                }
            }
        }

        boolean isDataProperty(Class<?> type, String fieldName) {
            return classDataProperties.getOrDefault(type, new HashSet<>()).contains(fieldName);
        }

        boolean isObjectProperty(Class<?> type, String fieldName) {
            return classObjectProperties.getOrDefault(type, new HashSet<>()).contains(fieldName);
        }
    }
}