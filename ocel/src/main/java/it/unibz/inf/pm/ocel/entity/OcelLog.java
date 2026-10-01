package it.unibz.inf.pm.ocel.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * an OCEL contains a global log, global event, and global object element.
 */

@Builder
@Data
public class OcelLog {
    @JsonProperty("ocel:global-log")
    private Map<String, OcelAttribute> globalLog;

    @JsonProperty("ocel:global-events")
    private Map<String, String> globalEvents;

    @JsonProperty("ocel:global-events")
    private Map<String, String> globalObjects;

    @JsonProperty("ocel:events")
    @NonNull
    @Getter
    private Map<String, OcelEvent> events;

    @JsonProperty("ocel:objects")
    @Getter
    private Map<String, OcelObject> objects;

    @JsonIgnore
    private Set<String> objectTypes;

    @JsonIgnore
    private Set<String> attributeNames;

    @JsonIgnore
    private List<ZonedDateTime> allTimestamps;

    @JsonIgnore
    private Map<String, OcelAttribute> attributeMap;


    public List<ZonedDateTime> getAllTimestamps() {
        if (allTimestamps == null) {
            allTimestamps = new ArrayList<>();
            for (OcelEvent event : events.values()) {
                allTimestamps.add(event.getTimestamp());
            }
        }
        return allTimestamps;
    }

    public Set<String> getAttributeNames() {
        if (attributeNames == null) {
            // Check if present in attribute form
            attributeNames = new HashSet<>();
            attributeNames.addAll(attributeMap.keySet());
        }
        return attributeNames;
    }

    public Set<String> getObjectTypes() {
        if (objectTypes == null) {
            objectTypes = new HashSet<>();
            objectTypes.addAll(objects.values().stream().map(OcelObject::getType).collect(Collectors.toSet()));
        }
        return objectTypes;
    }

    public Set<String> getRelationTypes() {
        if (objectTypes == null) {
            objectTypes = new HashSet<>();
            objectTypes.addAll(objects.values().stream().map(OcelObject::getType).collect(Collectors.toSet()));
        }
        return objectTypes;
    }
}
