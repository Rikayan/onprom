package it.unibz.inf.onprom.obdamapper;

import com.google.common.collect.ImmutableList;
import it.unibz.inf.onprom.data.query.AnnotationQueries;
import it.unibz.inf.onprom.obdamapper.utility.OntopUtility;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.injection.SQLPPMappingFactory;
import it.unibz.inf.ontop.spec.mapping.PrefixManager;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPTriplesMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class AQRtoOBDA {
    private final AnnotationQueries source;
    private final SQLPPMappingFactory ppMappingFactory;

    AQRtoOBDA(SQLPPMapping sourceObdaModel, Properties dataSourceProperties, AnnotationQueries src) {
        this.source = src;
        OntopSQLOWLAPIConfiguration config = OntopUtility.getConfiguration(sourceObdaModel, dataSourceProperties);
        this.ppMappingFactory = config.getInjector().getInstance(SQLPPMappingFactory.class);
    }
}
