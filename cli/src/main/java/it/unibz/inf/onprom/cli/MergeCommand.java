package it.unibz.inf.onprom.cli;

import it.unibz.inf.onprom.obdamapper.OBDAMapper;
import it.unibz.inf.onprom.obdamapper.utility.OntopUtility;
import it.unibz.inf.onprom.ui.utility.IOUtility;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import org.semanticweb.owlapi.model.OWLOntology;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import static picocli.CommandLine.*;

@Command(name = "merge")
public class MergeCommand implements Callable<Integer> {
    @Spec
    Model.CommandSpec spec;

    @ArgGroup(exclusive = false, heading = "Mapping Mode Options:%n")
    AnnotationCommand.MappingGroup mappingGroup;

    @Option(names = {"--eventOnt", "-E"}, defaultValue = "xes", converter = EventSchemaOntologyConverter.class)
    OWLOntology eventOnt;

    @Parameters(index = "0", description = "Combined Mapping")
    File mappingsFile;

    @Override
    public Integer call() throws Exception {
        DomainPaths domainPaths = DomainPaths.resolveDomainPaths(mappingGroup.defs, spec);
        AnnotationCommand.SourceManifestations genCommandArgs = AnnotationCommand.loadCommandArgs(domainPaths,
                true);
        SQLPPMapping eventMapping = OntopUtility.getOBDAModel(mappingGroup.mappingFile, genCommandArgs.dsProperties());


        SQLPPMapping ebdaModel = new OBDAMapper(genCommandArgs.domainOnt(),
                eventOnt,
                genCommandArgs.domainOBDA(),
                genCommandArgs.dsProperties(),
                eventMapping
        ).getOBDAModel();

        OntopUtility.saveModel(ebdaModel, mappingsFile);

        return 0;
    }
}
