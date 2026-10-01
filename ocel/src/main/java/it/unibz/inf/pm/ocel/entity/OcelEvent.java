package it.unibz.inf.pm.ocel.entity;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * an event contains the id, activity, timestamp, omap, and vmap elements.
 */


@Slf4j
public class OcelEvent extends OcelPart implements Comparable<OcelEvent> {
    @Getter
    @JSONField(name = "ocel:activity")
    private String activity;

    @Getter
    @JSONField(name = "ocel:timestamp")
    private ZonedDateTime timestamp;

    @Getter
    @JSONField(name = "ocel:omap")
    private List<String> omap;

    @Getter
    @JSONField(name = "ocel:vmap")
    private Map<String, OcelAttribute> vmap;

    @Builder
    public OcelEvent(String id,  String activity, ZonedDateTime timestamp, List<String> omap, Map<String, OcelAttribute> vmap) {
        this.id = id;
        this.activity = activity;
        this.timestamp = timestamp;
        this.omap = omap;
        this.vmap = vmap;
    }

    public OcelEvent(String id,  String activity, ZonedDateTime timestamp, List<String> omap) {
        this(id, activity, timestamp, omap, new HashMap<>());
    }

    public OcelEvent(String id,  String activity, ZonedDateTime timestamp) {
        this(id, activity, timestamp, new ArrayList<>(), new HashMap<>());
    }

    public OcelEvent(String s) {
        this.id = s;
        this.omap = new ArrayList<>();
        this.vmap = new HashMap<>();
    }

    public String getTimestampAsString() {
        return timestamp.format(DateTimeFormatter.ISO_DATE_TIME);
    }

    @Override
    public String getDescription() {
        return "Event " + this.id + "observing " + this.activity + " activity at time " + this.timestamp;
    }

    @Override
    public int compareTo(@NonNull OcelEvent o) {
        if (this.timestamp.isBefore(o.timestamp)) {
            return -1;
        }  else if (this.timestamp.isAfter(o.timestamp)) {
            return 1;
        }
        if (!this.id.equals(o.id)) {
            return this.id.compareTo(o.id);
        }
        log.warn("{} is the same as {}", this.getDescription(), o.getDescription());
        return 0;
    }

    public void setTimestamp(String timestamp) {
        if (this.timestamp != null) {
            log.warn("Only allowed to set timestamp once! Ignoring repeated timestamp change attempt...");
            return;
        }
        this.timestamp = parseTimestamp(timestamp);
    }

    private static ZonedDateTime parseTimestamp(String timestamp) {

        LocalDateTime localDateTime = null;
        try {
            return ZonedDateTime.parse(timestamp);
        } catch (Exception e) {
            try {
                localDateTime = LocalDateTime.parse(timestamp);
            } catch (DateTimeParseException e2) {
                try {
                    localDateTime = LocalDateTime.from(LocalDate.parse(timestamp));
                } catch (DateTimeParseException ex) {
                    try {
                        localDateTime = LocalDateTime.from(LocalTime.parse(timestamp));
                    } catch (DateTimeParseException exc) {
                        log.error("Failed to parse timestamp of event: {}", exc.getMessage());
                        throw exc;
                    }
                }
            }
        }
        log.info("Assumed that {} has the same timezone as you", timestamp);
        return localDateTime.atZone(ZoneId.of(ZoneId.systemDefault().getId()));
        // Assume occurred at same timezone as user
    }

    public void setActivity(String activity) {
        if (this.activity != null) {
            log.warn("Ignoring attempt to change activity of event {} from {} to {}...", this.id, this.activity,
                    activity);
        } else
            this.activity = activity;
    }

    public static class OcelEventBuilder {
        private ZonedDateTime timestamp;
        public OcelEventBuilder timestamp(String timestamp) {
            this.timestamp = parseTimestamp(timestamp);
            return this;
        }
        public OcelEventBuilder timestamp(ZonedDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
    }
}