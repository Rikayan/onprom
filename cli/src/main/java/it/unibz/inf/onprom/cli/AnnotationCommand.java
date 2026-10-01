package it.unibz.inf.onprom.cli;

import it.unibz.inf.onprom.AnnotationEditor;
import it.unibz.inf.onprom.data.Annotation;
import it.unibz.inf.onprom.data.FileType;
import it.unibz.inf.onprom.data.query.AnnotationQueries;
import it.unibz.inf.onprom.interfaces.Diagram;
import it.unibz.inf.onprom.interfaces.DiagramShape;
import it.unibz.inf.onprom.obdamapper.OBDAMapper;
import it.unibz.inf.onprom.obdamapper.utility.OntopUtility;
import it.unibz.inf.onprom.ui.utility.IOUtility;
import it.unibz.inf.onprom.ui.utility.UIUtility;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

import static picocli.CommandLine.*;
import static picocli.CommandLine.Model.*;

@Command(name = "annotate")
public class AnnotationCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Input Path")
    File inFile;

    @Option(names = "--no-edit", description = "Skip editing, directly export all")
    boolean noEdit;

    @Spec
    CommandSpec spec;

    @Option(names = "--gui", defaultValue = "false")
    boolean gui;

    @Option(names = {"--eventOnt", "-E"}, defaultValue = "xes", converter = EventSchemaOntologyConverter.class)
    OWLOntology eventOnt;

    // Parent exclusive group: One of these execution routes MUST be chosen
    @ArgGroup(multiplicity = "1", heading = "Processing Modes (Choose exactly one):%n")
    ProcessingMode mode;

    public static AnnotationCommand.SourceManifestations loadCommandArgs(DomainPaths domainPaths, boolean includeMapping)
            throws IOException, OWLOntologyCreationException {

        Properties dsProperties = new Properties();
        dsProperties.load(new FileInputStream(domainPaths.properties()));
        SQLPPMapping domainOBDA = includeMapping ?
                OntopUtility.getOBDAModel(domainPaths.mapping(), dsProperties) : null;
        OWLOntology domainOnt = OWLManager.createOWLOntologyManager()
                .loadOntologyFromOntologyDocument(domainPaths.model());
        return new SourceManifestations(dsProperties, domainOBDA, domainOnt);
    }

    static class ProcessingMode {
        // Route A: Provide an annotation query file
        @Option(names = {"--queries", "-Q"}, required = true, description = "Output Query Path (annotation-query file)")
        File queries;

        // Route B: Provide BOTH a mapping file and domain configurations
        @ArgGroup(exclusive = false, heading = "Mapping Mode Options:%n")
        MappingGroup mappingGroup;
    }

    static class MappingGroup {
        @Option(names = {"--mapping", "-M"}, required = true, description = "Mapping file path")
        File mappingFile;

        @Option(names = {"--domain", "-D"}, required = true, description =
                "Key-value pairs in KEY=VALUE format (no spaces around '=')")
        Map<String, String> defs = new HashMap<>();
    }

    @Override
    public Integer call() throws Exception {
        if(noEdit && gui) {
            throw new IllegalArgumentException("Cannot use both --no-edit and --gui together");
        }
        if(mode.queries != null && !(IOUtility.getFileType(mode.queries) == FileType.QUERIES))
            throw new IllegalArgumentException("Output queries File %s must be of type ".formatted(mode.queries) +
                    FileType.QUERIES);
        if(mode.mappingGroup != null && !(IOUtility.getFileType(mode.mappingGroup.mappingFile) == FileType.MAPPING)) {
            throw new IllegalArgumentException("Output mapping File %s must be of type "
                    .formatted(mode.mappingGroup.mappingFile) + FileType.MAPPING);
        }

        Optional<Set<DiagramShape<? extends Diagram>>> annotations;
        Optional<OWLOntology> ontology;
        FileType fileType = IOUtility.getFileType(inFile);
        if(fileType == FileType.ANNOTATION) {
            annotations = Optional.ofNullable(IOUtility.importJSON(this.inFile));
            ontology = Optional.empty();
        } else if(fileType == FileType.ONTOLOGY) {
            annotations = Optional.empty();
            ontology = Optional.of(OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(inFile));
        } else
            throw new IllegalArgumentException("Input file must be of type " + FileType.ANNOTATION + " or "
                    + FileType.ONTOLOGY + " was " + fileType);
        if(annotations.isEmpty()) {
            System.out.println("Provided ontology does not contain annotations. Using GUI mode");
            gui = true;
        }

        if(gui) {
            AnnotationEditor annotationEditor = new AnnotationEditor(eventOnt, null, null,
                    true);
            annotations.ifPresentOrElse(ann -> annotationEditor.load("", ann),
                    () -> annotationEditor.loadOntology("", ontology.orElseThrow()));
            JFrame frame = new JFrame();
            frame.getContentPane().setLayout(new BorderLayout());
            frame.getContentPane().add(annotationEditor, BorderLayout.CENTER);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    AnnotationQueries output = annotationEditor.buildAnnotationQueriesAndChoose();
                    super.windowClosing(e);
                    UIUtility.stopWorkers();
                    if(mode.queries != null)
                        IOUtility.exportJSON(mode.queries, output);
                    else {
                        SQLPPMapping mappings = convertAnnQueriesToMappings(
                                DomainPaths.resolveDomainPaths(mode.mappingGroup.defs, spec), eventOnt, output);
                        OntopUtility.saveModel(mappings, mode.mappingGroup.mappingFile);
                    }
                }
            });
        } else {
            AnnotationQueries annotationQueries = new AnnotationQueries();
            annotations.get().stream()
                    .filter(Annotation.class::isInstance).map(Annotation.class::cast).map(Annotation::getQuery)
                    .forEach(annotationQueries::addQuery);
            if(!noEdit) {
                File queriesOrTemp = mode.queries != null ? mode.queries
                        : File.createTempFile("queries", ".json");
                edit(annotationQueries, queriesOrTemp);
                annotationQueries = IOUtility.readJSON(queriesOrTemp, AnnotationQueries.class)
                        .orElseThrow(IllegalArgumentException::new);
            }
            if (mode.mappingGroup != null) {
                SQLPPMapping mappingsFile = convertAnnQueriesToMappings(
                        DomainPaths.resolveDomainPaths(mode.mappingGroup.defs, spec), eventOnt, annotationQueries);
                OntopUtility.saveModel(mappingsFile, mode.mappingGroup.mappingFile);
            } else
                IOUtility.exportJSON(mode.queries, annotationQueries);
        }
        return 0;
    }

    private SQLPPMapping convertAnnQueriesToMappings(DomainPaths domainPaths, OWLOntology targetOntology,
                                                     AnnotationQueries annotationQueries) {
        SourceManifestations sourceArgs;
        try {
            sourceArgs = loadCommandArgs(domainPaths, true);
        } catch (IOException | OWLOntologyCreationException e) {
            throw new RuntimeException(e);
        }
        return new OBDAMapper(sourceArgs.domainOnt(), targetOntology, sourceArgs.domainOBDA(),
                sourceArgs.dsProperties(), annotationQueries, false).getOBDAModel();
    }

    private void edit(AnnotationQueries annotationsQueries, File queriesOrTemp) {
        AnnotationQueryEditor annotationQueryEditor = new AnnotationQueryEditor(annotationsQueries, queriesOrTemp);
        annotationQueryEditor.edit();
    }

    private ProcessBuilder getEditorCommands(String filename) {
        String editor = System.getenv("EDITOR");
        if(editor != null && !editor.isBlank())
            return new ProcessBuilder(editor, filename);
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            return new ProcessBuilder("notepad.exe", filename);
        } else if (os.contains("mac")) {
            return new ProcessBuilder("open", "-W", "-e", filename);
        }
        else {
            ProcessBuilder pb = new ProcessBuilder("nano", filename);
            pb.inheritIO();
            return pb;
        }
    }

    public record SourceManifestations(Properties dsProperties, SQLPPMapping domainOBDA, OWLOntology domainOnt) {
    }
}
