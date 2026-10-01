/*
 * onprom-obdamapper
 *
 * OntopUtility.java
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

package it.unibz.inf.onprom.obdamapper.utility;

import com.google.inject.Injector;
import it.unibz.inf.ontop.exception.InvalidMappingException;
import it.unibz.inf.ontop.exception.MappingIOException;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.protege.core.OldSyntaxMappingConverter;
import it.unibz.inf.ontop.spec.mapping.parser.impl.OntopNativeMappingParser;
import it.unibz.inf.ontop.spec.mapping.pp.SQLPPMapping;
import it.unibz.inf.ontop.spec.mapping.serializer.impl.OntopNativeMappingSerializer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class OntopUtility {
    public static synchronized Properties getDataSourceProperties(File obdaFile) {
        try {
            OldSyntaxMappingConverter converter = new OldSyntaxMappingConverter(new FileReader(obdaFile), obdaFile.getName());
            return converter.getDataSourceProperties().orElse(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static OntopSQLOWLAPIConfiguration getConfiguration(//OBDAModel obdaModel,
                                                               SQLPPMapping mapping,
                                                               Properties dataSourceProperties) {
        return OntopSQLOWLAPIConfiguration.defaultBuilder()
                .ppMapping(mapping)
                .properties(dataSourceProperties)
                .build();
    }

    public static SQLPPMapping getOBDAModel(File obdaFile, File propertiesFile) {
        Properties properties = new Properties();
        try {
            properties.load(new FileReader(propertiesFile));
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return getOBDAModel(obdaFile, properties);
    }

    public static SQLPPMapping getOBDAModel(File obdaFile, Properties dataSource) {
        Injector injector = OntopSQLOWLAPIConfiguration.defaultBuilder()
                .properties(dataSource)
                .build().getInjector();
        OntopNativeMappingParser parser = injector.getInstance(OntopNativeMappingParser.class);
        try {
            return parser.parse(obdaFile);
        } catch (InvalidMappingException | MappingIOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void saveModel(SQLPPMapping model, File file) {
        try {
            OntopNativeMappingSerializer writer = new OntopNativeMappingSerializer();
            writer.write(file, model);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
