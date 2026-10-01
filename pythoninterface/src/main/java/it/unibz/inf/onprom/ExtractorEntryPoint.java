package it.unibz.inf.onprom;

import it.unibz.inf.onprom.data.FileType;
import it.unibz.inf.onprom.data.query.AnnotationQueries;
import it.unibz.inf.onprom.interfaces.Diagram;
import it.unibz.inf.onprom.interfaces.DiagramShape;
import it.unibz.inf.onprom.logextractor.ocel.OCELLogExtractor;
import it.unibz.inf.onprom.obdamapper.utility.OntopUtility;
import it.unibz.inf.onprom.ui.utility.IOUtility;
import it.unibz.inf.onprom.ui.utility.UIUtility;
import it.unibz.inf.onprom.ui.utility.UMLEditorMessages;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import it.unibz.inf.pm.ocel.entity.OcelEvent;
import it.unibz.inf.pm.ocel.entity.OcelLog;
import lombok.Getter;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@Getter
public class ExtractorEntryPoint {
    private final OCELLogExtractor extractor;
    private OWLOntology domainOntology;
    private OWLOntology targetOntology;
    private AnnotationQueries targetAnnotation;
    private AnnotationQueries domainAnnotation;
    private Properties dsProperties;
    private SQLPPMapping obdaModel;
    private static final Logger logger = LoggerFactory.getLogger(ExtractorEntryPoint.class);
    private Set<String> entities;
    private OWLOntologyManager manager;
    private Set<String> staticTypes;

    public ExtractorEntryPoint() {
        extractor = new OCELLogExtractor();
    }

    public void loadFile(String path) {
        loadFile(path, true);
    }

    public void loadFile(File path) {
        loadFile(path, true);
    }

    public Map<String, Map<String, Integer>> getStats() {
        System.out.println("Started log...");
        OcelLog log = this.getPartialLog(null, null);
        // Get maximum total count, max per activity. Make a plot of the total counts and one for each activity
        System.out.println("Done log...");
        Map<String, Map<String, Integer>> reverseMap = new HashMap<>();
        long startTime = Instant.now().getEpochSecond();
        int count = 0;
        int inAll = log.getEvents().size();
        long soFar;
        System.out.println(inAll + " events to process!");
        for (OcelEvent event : log.getEvents().values()) {
            count += 1;
            if (count % 1000 == 0) {
                soFar =  Instant.now().getEpochSecond() - startTime;
                System.out.println("Done " + count);
                System.out.println("ETA: " + soFar / ((double) count) * (inAll - count) + " seconds");
            }
            List<String> omap = event.getOmap();
            String activity = event.getActivity();
            if (!reverseMap.containsKey(activity)) {
                reverseMap.put(activity, new HashMap<>());
            }
            Map<String, Integer> objCountMap = reverseMap.get(activity);
            if (omap != null) {
                for (String key : omap) {
                    objCountMap.put(key, objCountMap.getOrDefault(key, 0) + 1);
                }
            }
        }
        System.out.println("Computing total count");
        Map<String, Integer> totalCounts = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> actMapEntry : reverseMap.entrySet()) {
            for (Map.Entry<String, Integer> objCountEntry : actMapEntry.getValue().entrySet()) {
                totalCounts.put(objCountEntry.getKey(), totalCounts.getOrDefault(objCountEntry.getKey(), 0) +
                        objCountEntry.getValue());
            }
        }
        reverseMap.put("__totalCounts", totalCounts);
        return reverseMap;
    }

    public void loadEntitiesWithTypes(Set<String> entities, Set<String> staticTypes) {
        this.entities = entities;
        this.staticTypes = staticTypes;
    }

    public void loadFile(String path, boolean isDomain) {
        logger.debug("Loading file: {}", path);
        File selectedFile = new File(path);
        loadFile(selectedFile, isDomain);
    }

    public void loadFile(File selectedFile, boolean isDomain) {
        if (!selectedFile.exists()) {
            logger.error("File {} does not exist", selectedFile.getAbsolutePath());
        }

        try {
            switch(IOUtility.getFileType(selectedFile)) {
                case ONTOLOGY:
                    loadOntology(selectedFile, isDomain);
                    break;
                case ANNOTATION:
                    Set<DiagramShape<? extends Diagram>> shapes = IOUtility.importJSON(selectedFile);
                    AnnotationEditor editor = new AnnotationEditor(null, true);
                    editor.load("", shapes);
                    AnnotationQueries annoQueries = editor.buildAnnotationQueriesAndChoose();
                    loadAnnotations(annoQueries, isDomain);
                    Optional<File> file = Optional.empty();
                    if (UIUtility.confirm(UMLEditorMessages.SAVE_FILE)) {
                        file = UIUtility.selectFileToSave(FileType.QUERIES);
                    }
                    if (file.isPresent()) {
                        IOUtility.exportJSON(file.get(), annoQueries);
                        file.get();
                        return;
                    }
                    break;
                case QUERIES:
                    loadAnnotations(selectedFile, isDomain);
                    break;
                case DS_PROPERTIES:
                    loadProperties(selectedFile);
                    break;
                case MAPPING:
                    loadMapping(selectedFile);
                    break;
            }
        } catch (OWLOntologyCreationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void resetConfiguration() {
        this.domainOntology = null;
        this.targetOntology = null;
        this.targetAnnotation = null;
        this.domainAnnotation = null;
        this.dsProperties = null;
        this.obdaModel = null;
        this.entities = null;
        this.staticTypes = null;
    }

    public List<String> getEntities() {
        int res = resolveConfiguration();
        if (res == 0) {
            logger.warn("Invalid configuration. Exiting attempt to get entities!");
        } else if (res == 1) {
            try {
                return extractor.getObjects(domainOntology, obdaModel, dsProperties, domainAnnotation, targetOntology,
                        targetAnnotation);
            } catch (Exception e) {
                logger.error("Error getting objects from custom target ontology and annotation: {}", e.getMessage());
            }
        } else {
            try {
                return extractor.getObjects(domainOntology, obdaModel, dsProperties, domainAnnotation);
            } catch (Exception e) {
                logger.error("Error getting objects: {}", e.getMessage());
            }
        }
        return null;
    }

    public OcelLog getPartialLog() {
        if (this.entities == null) {
            throw new RuntimeException("Could not find any entities loaded, cannot obtain partial log");
        }
        return getPartialLog(this.entities, this.staticTypes);
    }

    public OcelLog getPartialLog(Set<String> entities, Set<String> staticTypes) {
        OcelLog ocelLog = null;
        int res = resolveConfiguration();
        if (res == 0) {
            logger.warn("Invalid configuration. Exiting attempt to extract partial log!");
        } else if (res == 1) {
            try {
                ocelLog = extractor.extractLog(entities, staticTypes, domainOntology, obdaModel, dsProperties, domainAnnotation, targetOntology,
                        targetAnnotation);
            } catch (Exception e) {
                logger.error("Error extracting log from custom target ontology and annotation: {}", e.getMessage());
            }
        } else {
            try {
                ocelLog = extractor.extractLog(entities, staticTypes, domainOntology, obdaModel, dsProperties, domainAnnotation);
            } catch (Exception e) {
                logger.error("Error extracting log: {}", e.getMessage());
            }
        }
        return ocelLog;
//        return List.of(ocelLog, ocelLog.getObjects().values().stream().map(o -> o.getOvmap().values().stream().map(a -> List.of(a.getType(), a.getThing().getValue()))));
    }


    private int resolveConfiguration() {
        if (targetOntology != null) {
            if (domainOntology == null || obdaModel == null || dsProperties == null || domainAnnotation == null
                    || targetAnnotation == null) {
                System.err.println("Please select domain ontology, OBDA mappings, Datasource Properties, " +
                        "target ontology, domain ontology annotations and target ontology annotations!");
            } else {
                return 1;
            }
        } else {
            if (domainOntology == null || obdaModel == null || dsProperties == null || domainAnnotation == null) {
                System.err.println("Please select domain ontology, OBDA mappings, Datasource properties, and domain to " +
                        "event ontology annotations!");
            } else {
                return 2;
            }
        }
        return 0;
    }

    private void loadOntology(File selectedFile, boolean isDomain) throws OWLOntologyCreationException {
        OWLOntology ont = OWLManager.createOWLOntologyManager()
                .loadOntologyFromOntologyDocument(selectedFile);
        if (isDomain)
            domainOntology = ont;
        else
            targetOntology = ont;
    }

    private void loadAnnotations(AnnotationQueries annoQ, boolean isDomain) {
        if (isDomain)
            domainAnnotation = annoQ;
        else
            targetAnnotation = annoQ;
    }

    private void loadAnnotations(File selectedFile, boolean isDomain) {
        AnnotationQueries queries = IOUtility.readJSON(selectedFile, AnnotationQueries.class).orElse(null);
        loadAnnotations(queries, isDomain);
    }

    public OWLOntology getOntologyFromFile(String filePath) {
        if (manager == null) {
            manager = OWLManager.createOWLOntologyManager();
        }
        try {
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(filePath));
            ontology.getAxioms().forEach(ax -> System.out.println(ax.getAxiomType().getName()));
            return ontology;
        } catch (OWLOntologyCreationException e) {
            throw new RuntimeException(e);
        }
    }

    public List<RelationTypeInstance> getRelationsFromFile(String path) {
        List<RelationTypeInstance> relInstances = new ArrayList<>();
        OWLOntology ontology = this.getOntologyFromFile(path);
        for (OWLObjectProperty objectProperty : ontology.getObjectPropertiesInSignature()) {
            final IRI objectPropertyIRI = objectProperty.getIRI();
            OWLClass domain, range;
            OWLClassExpression domainClassExpression = EntitySearcher.getDomains(objectProperty, ontology).findFirst().orElse(null);
            if (domainClassExpression instanceof OWLClass) {
                domain = domainClassExpression.asOWLClass();
                OWLClassExpression rangeClassExpression = EntitySearcher.getRanges(objectProperty, ontology).findFirst().orElse(null);
                if (rangeClassExpression instanceof OWLClass) {
                    range = rangeClassExpression.asOWLClass();
                    relInstances.add(new RelationTypeInstance(domain.getIRI().getShortForm(), range.getIRI().getShortForm(),
                            objectPropertyIRI.getShortForm()));
                }
            }
        }
        return relInstances;
    }


    private void loadProperties(File selectedFile) throws IOException {
        dsProperties = new Properties();
        dsProperties.load(new FileInputStream(selectedFile));
    }

    private void loadMapping(File selectedFile) throws OWLOntologyCreationException {
        if (dsProperties == null) {
            System.err.println("Please load database connection properties file first!");
        } else {
            obdaModel = OntopUtility.getOBDAModel(selectedFile, dsProperties);
        }
    }

    public static void main(String[] args) {
        GatewayServer gatewayServer = new GatewayServer(new ExtractorEntryPoint());
        gatewayServer.start();
        System.out.println("Gateway Server Started");
    }
}
