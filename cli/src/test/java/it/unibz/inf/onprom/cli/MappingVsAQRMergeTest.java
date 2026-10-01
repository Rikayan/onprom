package it.unibz.inf.onprom.cli;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import it.unibz.inf.onprom.data.query.AnnotationQueries;
import it.unibz.inf.onprom.obdamapper.OBDAMapper;
import it.unibz.inf.onprom.obdamapper.PrefixManagerComparator;
import it.unibz.inf.onprom.ui.utility.IOUtility;
import it.unibz.inf.ontop.model.atom.DistinctVariableOnlyDataAtom;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.Variable;
import it.unibz.inf.ontop.model.type.TermTypeInference;
import it.unibz.inf.ontop.spec.mapping.PrefixManager;
import it.unibz.inf.ontop.spec.mapping.TargetAtom;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertFalse;

public class MappingVsAQRMergeTest {
    private AnnotationCommand.SourceManifestations sourceArgs;
    private AnnotationQueries annotationQueries;
    private OWLOntology targetOntology;
    private SQLPPMapping l2OBDA;

    private File pathFromTestResource(String fileName) {
        try {
            return Paths.get(
                    Objects.requireNonNull(
                            MappingVsAQRMergeTest.class
                                    .getClassLoader()
                                    .getResource(fileName)
                    ).toURI()).toFile();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setUp() throws Exception {
        // Transform AQR to OBDA
        String sourceOWL = "OCELERP.owl";
        File sourceOWLFile = pathFromTestResource(sourceOWL);
        String sourceProp = "OCELERP.properties";
        File sourcePropFile = pathFromTestResource(sourceProp);
        String l1OBDA = "OCELERP.obda";
        File l1OBDAFile = pathFromTestResource(l1OBDA);
        String l2AQR = "OCELERPforOCELv2_corrected.aqr";
        File l2AQRFile = pathFromTestResource(l2AQR);
        String targetOnt = "OCELv2.owl";
        File targetOntFile = pathFromTestResource(targetOnt);

        sourceArgs = AnnotationCommand.loadCommandArgs(new DomainPaths(sourceOWLFile, sourcePropFile, l1OBDAFile), true);
        annotationQueries = IOUtility.readJSON(l2AQRFile, AnnotationQueries.class)
                .orElseThrow(IllegalArgumentException::new);
        targetOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(targetOntFile);

        l2OBDA = new OBDAMapper(sourceArgs.domainOnt(), targetOntology, sourceArgs.domainOBDA(),
                sourceArgs.dsProperties(), annotationQueries, false).getOBDAModel();
    }

    @Test
    public void testOptions() {
        SQLPPMapping merged1 = new OBDAMapper(sourceArgs.domainOnt(), targetOntology, sourceArgs.domainOBDA(),
                sourceArgs.dsProperties(), annotationQueries).getOBDAModel();
        SQLPPMapping merged2 = new OBDAMapper(sourceArgs.domainOnt(), targetOntology, sourceArgs.domainOBDA(),
                sourceArgs.dsProperties(), l2OBDA).getOBDAModel();
        Javers javers = JaversBuilder.javers()
                .registerValue(PrefixManager.class, new PrefixManagerComparator())
                .registerValueGsonTypeAdapter(TargetAtom.class, new TargetAtomTypeAdapter()).build();
        Diff diff = javers.compare(merged1, merged2);
        assertFalse(diff.prettyPrint(), diff.hasChanges());
    }

    public static class TargetAtomTypeAdapter extends TypeAdapter<TargetAtom> {
        @Override
        public void write(JsonWriter out, TargetAtom value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            out.beginObject();

            // 1. Predicate IRI
            out.name("predicateIRI");
            if (value.getPredicateIRI().isPresent()) {
                out.value(value.getPredicateIRI().get().getIRIString());
            } else {
                out.nullValue();
            }

            // 2. Projection atom
            out.name("projectionAtom");
            out.value(renderProjectionAtom(value.getProjectionAtom()));

            // 3. Substituted terms
            out.name("substitutedTerms");
            out.beginArray();
            for (ImmutableTerm t : value.getSubstitutedTerms()) {
                out.value(renderTerm(t));
            }
            out.endArray();

            out.endObject();
        }

        private String renderTerm(ImmutableTerm term) {
            if (term == null) return "null";

            if (term.isNull()) return "NULL";

            String type = term.inferType()
                    .flatMap(TermTypeInference::getTermType)
                    .map(Object::toString)
                    .orElse("UNKNOWN");

            List<String> vars = term.getVariableStream()
                    .map(Variable::getName)
                    .sorted()
                    .toList();

            return type + ":" + String.join(",", vars);
        }

        private String renderProjectionAtom(DistinctVariableOnlyDataAtom atom) {
            if (atom == null) return "null";
            return atom.toString();
        }

        @Override
        public TargetAtom read(JsonReader in) throws IOException {
            return null;
        }
    }
}