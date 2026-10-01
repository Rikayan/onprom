package it.unibz.inf.onprom.cli;

import java.util.logging.Logger;

import it.unibz.inf.onprom.data.FileType;
import it.unibz.inf.onprom.logextractor.custom.CustomLogExtractor;
import it.unibz.inf.onprom.logextractor.ocel.OCELLogExtractor;
import it.unibz.inf.onprom.logextractor.xes.XESLogExtractor;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.out.XesXmlGZIPSerializer;
import org.semanticweb.owlapi.apibinding.OWLManager;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.*;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import it.unibz.inf.onprom.obdamapper.OBDAMapper;
import it.unibz.inf.onprom.obdamapper.utility.OntopUtility;
import it.unibz.inf.onprom.ui.utility.IOUtility;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import it.unibz.inf.pm.custom.OWLClassGenerator;
import org.semanticweb.owlapi.model.OWLOntology;
import picocli.CommandLine.Model.CommandSpec;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static picocli.CommandLine.*;

@Command(name = "generate")
public class GenerateCommand implements Callable<Integer> {
    @Spec
    CommandSpec spec;

    @Option(names = {"--domain", "-D"}, description = "Key-value pairs in KEY=VALUE format (no spaces around '=')")
    Map<String,String> defs = new HashMap<>();

    OWLOntology targetOnt;
    EventSchemaOntologyConverter.EventSchemaType targetSchemaType;
    private final Logger logger = Logger.getLogger(GenerateCommand.class.getName());

    @Option(names = {"--target-schema", "-T"},
            defaultValue = "xes")
    public void setTargetSchema(String value) throws Exception {
        value = value.trim();

        if (value.equals("xes")) {
            targetSchemaType = EventSchemaOntologyConverter.EventSchemaType.XES;
            targetOnt = XESLogExtractor.getOntology();
        }
        else if (value.equals("ocel")) {
            targetSchemaType = EventSchemaOntologyConverter.EventSchemaType.OCEL;
            targetOnt = OCELLogExtractor.getOntology();
        }
        else {
            targetSchemaType = EventSchemaOntologyConverter.EventSchemaType.CUSTOM;
            File file = DomainPaths.checkPath(value);
            targetOnt = OWLManager.createOWLOntologyManager()
                    .loadOntologyFromOntologyDocument(file);
        }
    }

    @Parameters(index = "0", description = "Combined Mapping")
    File mappingsFile;

    @Parameters(index = "1", description = "Output Path")
    File output;

    @Override
    public Integer call() throws Exception {
        DomainPaths domainPaths = DomainPaths.resolveDomainPaths(defs, spec, false);

        AnnotationCommand.SourceManifestations genCommandArgs = AnnotationCommand.loadCommandArgs(domainPaths,
                false);

        SQLPPMapping model;
        FileType fileType = IOUtility.getFileType(mappingsFile);
        if(fileType == FileType.MAPPING) {
            SQLPPMapping mergedModel = OntopUtility.getOBDAModel(mappingsFile, genCommandArgs.dsProperties());
            model = new OBDAMapper(targetOnt, mergedModel, genCommandArgs.dsProperties()).getOBDAModel();

            if(targetSchemaType == EventSchemaOntologyConverter.EventSchemaType.CUSTOM) { // TODO: Test!
                OWLClassGenerator.ClassMetadata<?> LogClass = OWLClassGenerator.generateMainClass(targetOnt);
                CustomLogExtractor customLogExtractor = new CustomLogExtractor(targetOnt);
                Object eventLog = customLogExtractor.extractLog(model, genCommandArgs.dsProperties(), LogClass);

                ObjectMapper mapper = JsonMapper.builder()
                        .enable(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID)
                        .changeDefaultVisibility(vc -> vc
                                .withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                        .addModule(new DynamicClassModule())
                        .build();

                String json = mapper.writeValueAsString(eventLog);
                Files.writeString(output.toPath(), json);
            } else if(targetSchemaType == EventSchemaOntologyConverter.EventSchemaType.XES) {
                XESLogExtractor extractor = new XESLogExtractor();
                XLog xTraces = extractor.extractLog(model, genCommandArgs.dsProperties());
                new XesXmlGZIPSerializer().serialize(xTraces, Files.newOutputStream(output.toPath()));
            } else if(targetSchemaType == EventSchemaOntologyConverter.EventSchemaType.OCEL) {
                OCELLogExtractor extractor = new OCELLogExtractor();
                Object ocelLog = extractor.extractLog(model, genCommandArgs.dsProperties());
                ObjectMapper mapper = JsonMapper.builder()
                        .enable(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID)
                        .changeDefaultVisibility(vc -> vc
                                .withFieldVisibility(JsonAutoDetect.Visibility.ANY))
                        .build();
                String json = mapper.writeValueAsString(ocelLog);
                Files.writeString(output.toPath(), json);
            }
        } else
            throw new IllegalArgumentException("Invalid file type " + fileType);

        return 0;
    }
}
