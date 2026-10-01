package it.unibz.inf.pm.ocel.entity;

import lombok.Getter;

import java.util.List;
import java.util.Map;

public class OcelPartCollection {

    public enum CollectionKind {LIST, ID_VALUE};
    public Map<String, OcelPart> idMapped;
    public List<OcelPart> values;
    @Getter
    private CollectionKind collectionKind;
    public OcelPartCollection(Map<String, OcelPart> idMapped) {
        this.idMapped = idMapped;
        this.collectionKind = CollectionKind.ID_VALUE;
    }

    public OcelPartCollection(List<OcelPart> idMapped) {
        this.values = idMapped;
        this.collectionKind = CollectionKind.LIST;
    }

    public List<OcelPart> getList() {
        if (this.collectionKind == CollectionKind.LIST) {
            return this.values;
        } else {
            throw new IllegalStateException("Collection is not list");
        }
    }

    public Map<String, OcelPart> getMap() {
        if (this.collectionKind == CollectionKind.ID_VALUE) {
            return this.idMapped;
        } else {
            throw new IllegalStateException("Collection is not map");
        }
    }
}
