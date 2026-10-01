package it.unibz.inf.onprom.logextractor;

import it.unibz.inf.onprom.data.query.AnnotationQueries;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.List;
import java.util.Properties;
import java.util.Set;

public interface Extractor<L> {
    L extractLog(SQLPPMapping ebdaModel, Properties dataSourceProperties) throws Exception;

    L extractLog(OWLOntology domainOntology, SQLPPMapping obdaModel, Properties dataSourceProperties, AnnotationQueries annotation) throws Exception;

    L extractLog(OWLOntology domainOnto, SQLPPMapping obdaModel, Properties dataSourceProperties, AnnotationQueries firstAnnoQueries, OWLOntology eventOntoVariant, AnnotationQueries secondAnnoQueries) throws Exception;

    default L extractLog(Set<String> interestingObjects, Set<String> staticTypes, OWLOntology domainOnto, SQLPPMapping obdaModel, Properties dataSourceProperties, AnnotationQueries firstAnnoQueries, OWLOntology eventOntoVariant, AnnotationQueries secondAnnoQueries) throws Exception {
        throw new UnsupportedOperationException("Partial Log extraction only supported for OCEL extraction");
    }

    default L extractLog(Set<String> interestingObjects, Set<String> staticTypes, OWLOntology domainOntology, SQLPPMapping obdaModel, Properties dataSourceProperties, AnnotationQueries annotation) throws Exception {
        throw new UnsupportedOperationException("Partial Log extraction only supported for OCEL extraction");
    }

    default L extractLog(Set<String> interestingObjects, Set<String> staticTypes, SQLPPMapping ebdaModel, Properties dataSourceProperties) throws Exception {
        throw new UnsupportedOperationException("Partial Log extraction only supported for OCEL extraction");
    }

    default List<String> getObjects(OWLOntology domainOnto, SQLPPMapping obdaModel, Properties dataSourceProperties, AnnotationQueries firstAnnoQueries, OWLOntology eventOntoVariant, AnnotationQueries secondAnnoQueries) throws Exception {
        throw new UnsupportedOperationException("Object extraction unsupported");
    }

    default List<String> getObjects(OWLOntology domainOntology, SQLPPMapping obdaModel, Properties dataSourceProperties, AnnotationQueries annotation) throws Exception {
        throw new UnsupportedOperationException("Object extraction unsupported");
    }

    default List<String> getObjects(SQLPPMapping ebdaModel, Properties dataSourceProperties) throws Exception {
        throw new UnsupportedOperationException("Object extraction unsupported");
    }

}
