package it.unibz.inf.onprom.obdamapper;

import com.github.jsonldjava.shaded.com.google.common.collect.ImmutableMap;
import it.unibz.inf.ontop.spec.mapping.PrefixManager;
import it.unibz.inf.ontop.spec.mapping.impl.AbstractPrefixManager;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OntologyExtendedPrefixManager extends AbstractPrefixManager {
    private final List<Map.Entry<String, String>> prefixMapList = new ArrayList<>();
    private final Map<String, String> prefixMap = new ConcurrentHashMap<>();

    public OntologyExtendedPrefixManager(PrefixManager prefixManager, OWLOntology targetOntology) {
        prefixMapList.addAll(prefixManager.getPrefixMap().entrySet());
        prefixMap.putAll(prefixMapList.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        targetOntology.getOntologyID().getOntologyIRI().ifPresent(
                (iri) -> {
                    String key = "log";
                    int uuidLength = 0;
                    while (prefixMap.containsKey(key)) {
                        String uuid = UUID.randomUUID().toString();
                        key = key.substring(0, key.length() - uuidLength) + uuid;
                        uuidLength = uuid.length();
                    }
                    prefixMap.put(key + ":", iri.toString());
                    prefixMapList.add(new AbstractMap.SimpleEntry<>(key + ":", iri.toString()));
                }
        );
    }

    @Override
    protected Optional<String> getIriDefinition(String prefix) {
        return Optional.ofNullable(prefixMap.get(prefix));
    }

    @Override
    protected List<Map.Entry<String, String>> getOrderedMap() {
        return prefixMapList;
    }

    @Override
    public Map<String, String> getPrefixMap() {
        return prefixMap;
    }
}
