package it.unibz.inf.onprom.cli;

import it.unibz.inf.onprom.logextractor.ocel.OCELLogExtractor;
import it.unibz.inf.onprom.logextractor.xes.XESLogExtractor;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import picocli.CommandLine;

import java.io.File;

public class EventSchemaOntologyConverter implements CommandLine.ITypeConverter<OWLOntology> {
    String XES = "xes", OCEL = "ocel";

    enum EventSchemaType {
        XES,
        OCEL,
        CUSTOM
    }

    @Override
    public OWLOntology convert(String value) throws Exception {
        if(value.equals(XES))
            return XESLogExtractor.getOntology();
        if(value.equals(OCEL))
            return OCELLogExtractor.getOntology();
        File eventSchemaFile = DomainPaths.checkPath(value.trim());
        return OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(eventSchemaFile);
    }
}
