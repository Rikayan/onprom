package it.unibz.inf.onprom.cli;

import it.unibz.inf.onprom.data.FileType;
import it.unibz.inf.onprom.ui.utility.IOUtility;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import picocli.CommandLine;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class DomainPaths {
    private static final Set<String> FULL = Set.of("model", "properties", "mapping");

    private static final Set<String> DIR = Set.of("dir", "prefix");
    private static final Set<String> FULL_WITHOUT_MAPPING = Set.of("model", "properties");
    private final File model;
    private final File properties;
    private final File mapping;

    private static final boolean INFER_MAPPING = true;

    public DomainPaths(File model, File properties, File mapping) {
        this.model = model;
        this.properties = properties;
        this.mapping = mapping;
    }


    public DomainPaths(String value, String value1, String value2) {
        this(checkPath(value), checkPath(value1), checkPath(value2));
    }

    public DomainPaths(String model, String properties) {
        this(checkPath(model), checkPath(properties));
    }

    private DomainPaths(File modelFile, File propertiesFile) {
        this.model = modelFile;
        this.properties = propertiesFile;
        this.mapping = null;
    }

    static File checkPath(String value2) {
        File path = Path.of(value2).toFile();
        if (!path.exists()) {
            throw new IllegalArgumentException("File " + value2 + " does not exist");
        }
        return path;
    }

    public static DomainPaths fromPathAndPrefix(String dir, String prefix, boolean includeMapping) {
        File base = checkPath(dir);
        File model = getUnique(base, prefix, FileType.ONTOLOGY);
        File properties = getUnique(base, prefix, FileType.DS_PROPERTIES);
        if(!INFER_MAPPING && !includeMapping)
            return new DomainPaths(model, properties);
        File mapping = getUnique(base, prefix, FileType.MAPPING);
        if(mapping == null && INFER_MAPPING)
            return new DomainPaths(model, properties);
        return new DomainPaths(model, properties, mapping);
    }

    private static File getUnique(File dir, String prefix, FileType fileType) {
        File result = null;
        for (File file : Objects.requireNonNull(dir.listFiles((FilenameFilter) new PrefixFileFilter(prefix)))) {
            if (IOUtility.getFileType(file) == fileType) {
                if (result != null)
                    throw new CommandLine.DuplicateNameException(("Ambiguous files of kind %s found for domain " +
                            "directory %s and prefix %s: ").formatted(fileType.toString(), dir, prefix) +
                            "%s and %s".formatted(result, file.getName()) + "!");
                result = file;
            }
        }
        return result;
    }

    static DomainPaths resolveDomainPaths(Map<String, String> defs, CommandLine.Model.CommandSpec spec) {
        return resolveDomainPaths(defs, spec, true);
    }

    static DomainPaths resolveDomainPaths(Map<String, String> defs, CommandLine.Model.CommandSpec spec,
                                          boolean includeMapping) {
        if (defs == null)
            return fromPathAndPrefix(".", "", includeMapping);
        Set<String> keys = defs.keySet().stream().map(String::trim).collect(Collectors.toSet());

        boolean fullMode = includeMapping ? keys.equals(FULL) : keys.equals(FULL_WITHOUT_MAPPING);

        if (!(fullMode || keys.equals(DIR))) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    """
                            Expected either:
                              -D model=... -D properties=... -D mapping=...
                            OR:
                              -D dir=... -D prefix=...
                            """
            );
        }

        if (fullMode) {
            if (includeMapping)
                return new DomainPaths(defs.get("model").trim(), defs.get("properties").trim(),
                        defs.get("mapping").trim());
            else
                return new DomainPaths(defs.get("model").trim(), defs.get("properties").trim());
        }

        return fromPathAndPrefix(
                defs.get("dir").trim(),
                defs.get("prefix").trim(), includeMapping);
    }

    public File model() {
        return model;
    }

    public File properties() {
        return properties;
    }

    public File mapping() {
        return mapping;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (DomainPaths) obj;
        return Objects.equals(this.model, that.model) &&
                Objects.equals(this.properties, that.properties) &&
                Objects.equals(this.mapping, that.mapping);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, properties, mapping);
    }

    @Override
    public String toString() {
        return "DomainPaths[" +
                "model=" + model + ", " +
                "properties=" + properties + ", " +
                "mapping=" + mapping + ']';
    }

}
