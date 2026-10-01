package it.unibz.inf.pm.ocel.entity;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class OcelObject extends OcelPart {
    @Getter
    @JsonProperty("ocel:type")
    private String type;

    @Getter
    @JsonProperty("ocel:ovmap")
    private Map<String, OcelAttribute> ovmap; //map with its child elements having a string value type. not required

    public OcelObject(String id) {
        this(id, "__INVALID__", new HashMap<>());
    }

    public OcelObject(String id, String type) {
        this(id, type, new HashMap<>());
    }

    @Builder
    public OcelObject(String id, String type, Map<String, OcelAttribute> ovmap) {
        this.id = id;
        this.type = type;
        this.ovmap = ovmap;
    }

    @Override
    public String getDescription() {
        return this.type + ": " + this.id + " with attributes " + this.ovmap.keySet();
    }

    public void setType(String type) {
        if (!this.type.equals("__INVALID__")) {
            if (!this.type.equals(type)) {
                log.warn("Type can only be set once. Ignoring attempt to change type of {} to {}",
                        this.getDescription(),
                        type);
            }
        }
        else
            this.type = type;
    }
}
