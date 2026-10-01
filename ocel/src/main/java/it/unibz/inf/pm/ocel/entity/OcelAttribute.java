package it.unibz.inf.pm.ocel.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

public class OcelAttribute extends OcelPart {
    @Getter
    private OcelElement thing;
    @Getter
    private String type;

    @Setter
    @Getter
    private OcelExtension extension;

    @Builder
    public OcelAttribute(String key, OcelElement value) {
        this(key, value, null, null);
    }

    private OcelAttribute(String key, OcelElement value, OcelExtension extension, String type) {
        this.id = key;
        this.thing = value;
        this.extension = extension;
        this.type = type;
    }

    public OcelAttribute(String key, OcelElement value, String type) {
        this(key, value, null, type);
    }

    @Override
    public String getDescription() {
        return "Attribute: " + this.getId() + ": " + this.thing.getValue();
    }

}
