package it.unibz.inf.pm.ocel.importer;

import it.unibz.inf.pm.ocel.OcelInitial;
import it.unibz.inf.pm.ocel.entity.OcelLog;
import org.dom4j.DocumentException;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class OcelXmlParser {
    public static OcelLog parse(File file) throws RuntimeException {
        String input_path = file.getAbsolutePath();
        OcelLog log = null;
        try {
            log = OcelInitial.import_log_as_map(input_path); // Always return OcelLog
        } catch (DocumentException | IOException e) {
            throw new RuntimeException(e);
        }
        return log;
    }
}
