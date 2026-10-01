/*
 * onprom-logextractor
 *
 * SimpleXESLogExtractor.java
 *
 * Copyright (C) 2016-2019 Free University of Bozen-Bolzano
 *
 * This product includes software developed under
 * KAOS: Knowledge-Aware Operational Support project
 * (https://kaos.inf.unibz.it).
 *
 * Please visit https://onprom.inf.unibz.it for more information.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.unibz.inf.onprom.logextractor.ocel;

import it.unibz.inf.onprom.data.query.AnnotationQueries;
import it.unibz.inf.onprom.logextractor.Extractor;
import it.unibz.inf.onprom.obdamapper.OBDAMapper;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import it.unibz.inf.pm.ocel.entity.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.*;

public class OCELLogExtractor implements Extractor<OcelLog> {
    private static final Logger logger = LoggerFactory.getLogger(OCELLogExtractor.class);

    public static OWLOntology getOntology() throws OWLOntologyCreationException {
        return OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(
                Objects.requireNonNull(OCELLogExtractor.class.getResourceAsStream(OCELConstants.eventOntoPath))
        );
    }

    public OcelLog extractLog(Set<String> objects, Set<String> staticTypes, OWLOntology domainOnto,
                              SQLPPMapping obdaModel, Properties dataSourceProperties,
                              AnnotationQueries firstAnnoQueries, OWLOntology eventOntoVariant,
                              AnnotationQueries secondAnnoQueries) throws Exception {
        SQLPPMapping obdaMapping = new OBDAMapper(domainOnto, eventOntoVariant, obdaModel,
                dataSourceProperties, firstAnnoQueries).getOBDAModel();
        return extractLog(objects, staticTypes, eventOntoVariant, obdaMapping, dataSourceProperties, secondAnnoQueries);
    }

    public OcelLog extractLog(OWLOntology domainOnto, SQLPPMapping obdaModel, Properties dataSourceProperties,
                              AnnotationQueries firstAnnoQueries, OWLOntology eventOntoVariant,
                              AnnotationQueries secondAnnoQueries) throws Exception {
        return extractLog(null, null, domainOnto, obdaModel, dataSourceProperties,
                firstAnnoQueries, eventOntoVariant, secondAnnoQueries);
    }

    public OcelLog extractLog(Set<String> objects, Set<String> staticTypes, OWLOntology domainOntology,
                              SQLPPMapping obdaModel, Properties dataSourceProperties,
                              AnnotationQueries annotation) throws Exception {
        logger.info("Constructing EBDA Mapping"); // TODO: check domain and event ontology assignment
        OWLOntology ontology = getOntology();
        SQLPPMapping ebdaModel = new OBDAMapper(domainOntology,
                ontology,
                obdaModel,
                dataSourceProperties,
                annotation
        ).getOBDAModel();
        return extractLog(objects, staticTypes, ebdaModel, dataSourceProperties);
    }

    public OcelLog extractLog(OWLOntology domainOntology, SQLPPMapping obdaModel, Properties dataSourceProperties,
                              AnnotationQueries annotation) throws Exception {
        return extractLog(null, null, domainOntology, obdaModel, dataSourceProperties, annotation);
    }

    public OcelLog extractLog(Set<String> interesting, Set<String> staticTypes, SQLPPMapping ebdaModel,
                              Properties dataSourceProperties) throws Exception {
        logger.info("Start extracting OCEL Log from the EBDA Mapping");
        long start = System.currentTimeMillis();
        OCELFactory factory = new OCELFactory();
        OCELEBDAReasoner ebdaR = new OCELEBDAReasoner(ebdaModel, dataSourceProperties, factory);
        ebdaR.printUnfoldedQueries();
        logger.info("Initialized reasoner in {} ms", System.currentTimeMillis() - start);
//        Map<String, OcelAttribute> attributes = new HashMap<>();
//        if (interesting != null) {
//            attributes = ebdaR.getAttributes();
//        }

        Map<String, OcelEvent> events = ebdaR.getEvents(interesting);
        Map<String, OcelObject> objects = ebdaR.getObjects(interesting, staticTypes);
        List<ZonedDateTime> allTimestamps = ebdaR.getAllTimestamps();
        Set<String> objectTypes = ebdaR.getObjectTypes();
        Set<String> attributeNames = ebdaR.getAttributeNames();
        Map<String, String> globalInfo = ebdaR.getGlobalInfo();
        ebdaR.dispose();

        OcelLog.OcelLogBuilder logBuilder = OcelLog.builder();
        logBuilder.objects(objects);
        logBuilder.events(events);
        logBuilder.globalLog(
                new HashMap<>(
                        Map.of("ocel:version",
                                new OcelAttribute("ocel:version",
                new OcelElement(globalInfo.get("ocel:version"))))));
        logBuilder.attributeNames(attributeNames);
        logBuilder.objectTypes(objectTypes);
        logBuilder.globalEvents(new HashMap<>() {
            private static final long serialVersionUID = 7243985670689201770L;

            {
            put("ocel-id", "__INVALID__");
            put("ocel-activity", "__INVALID__");
            put("ocel-timestamp", "__INVALID__");
            put("ocel-omap", "__INVALID__");
        }});

        logBuilder.globalObjects(new HashMap<>() {
            private static final long serialVersionUID = -5017763438705953751L;

            {
            put("ocel-id", "__INVALID__");
            put("ocel-type", "__INVALID__");
        }});

        logBuilder.allTimestamps(allTimestamps);
        return logBuilder.build();
    }

    @Override
    public List<String> getObjects(OWLOntology domainOnto, SQLPPMapping obdaModel, Properties dataSourceProperties, AnnotationQueries firstAnnoQueries, OWLOntology eventOntoVariant, AnnotationQueries secondAnnoQueries) throws Exception {
        SQLPPMapping obdaMapping = new OBDAMapper(domainOnto, eventOntoVariant, obdaModel, dataSourceProperties, firstAnnoQueries).getOBDAModel();
        return getObjects(eventOntoVariant, obdaMapping, dataSourceProperties, secondAnnoQueries);
    }

    @Override
    public List<String> getObjects(OWLOntology domainOntology, SQLPPMapping obdaModel, Properties dataSourceProperties, AnnotationQueries annotation) throws Exception {
        SQLPPMapping ebdaModel = new OBDAMapper(domainOntology,
                getOntology(),
                obdaModel,
                dataSourceProperties,
                annotation
        ).getOBDAModel();
        return getObjects(ebdaModel, dataSourceProperties);
    }

    @Override
    public List<String> getObjects(SQLPPMapping ebdaModel, Properties dataSourceProperties) throws Exception {
        OCELFactory factory = new OCELFactory();
        OCELEBDAReasoner ebdaR = new OCELEBDAReasoner(ebdaModel, dataSourceProperties, factory);
        return new ArrayList<>(ebdaR.getObjects(null, null).keySet());
    }

    public OcelLog extractLog(SQLPPMapping ebdaModel, Properties dataSourceProperties) throws Exception {
        return extractLog(null, null, ebdaModel, dataSourceProperties);
    }

    @Override
    public String toString() {
        return "OCEL Extractor";
    }

}



