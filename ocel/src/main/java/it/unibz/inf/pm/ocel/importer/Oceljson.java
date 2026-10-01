package it.unibz.inf.pm.ocel.importer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import it.unibz.inf.pm.ocel.entity.*;
import it.unibz.inf.pm.ocel.util.JsonUtil;
import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.units.qual.K;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Oceljson {

    static Logger log = LoggerFactory.getLogger(Oceljson.class);

    public static Object apply(String input_path) throws IOException {
        return JsonUtil.readJsonfileToObject(input_path);
    }

    public static OcelLog apply_map(String inputPath) throws IOException {
        OcelLog.OcelLogBuilder logBuilder = OcelLog.builder();
        File file = new File(inputPath);
        String jsonString = FileUtils.readFileToString(file, "UTF-8");
        Map<String, String> jsonObject = JSON.parseObject(jsonString, new TypeReference<>() {});
        String events = jsonObject.get("ocel:events");
        String objects = jsonObject.get("ocel:objects");

        String globalEvent = jsonObject.get("ocel:global-event");
        Map<String, String> globalEventMap = JSON.parseObject(globalEvent, new TypeReference<>() {});
        logBuilder.globalEvents(globalEventMap);

        String globalObject = jsonObject.get("ocel:global-object");
        Map<String, String> globalObjectMap = JSON.parseObject(globalEvent, new TypeReference<>() {});
        logBuilder.globalObjects(globalObjectMap);


        // save the events element
        Map<String, Object> eventsMap = JSON.parseObject(events, new TypeReference<>() {});
        List<OcelEvent> parsedEvents = new ArrayList<>();
        for (Map.Entry<String, Object> map : eventsMap.entrySet()){
            String key = map.getKey();
            Object value = map.getValue();
            JSONObject everyElementObject = JSON.parseObject(value.toString());
            if (!everyElementObject.containsKey("ocel:activity") || !everyElementObject.containsKey("ocel:timestamp"))
                log.warn("Ignoring event {}, activity or timestamp missing", key);
            OcelEvent.OcelEventBuilder eventBuilder = OcelEvent.builder().id(key);

            Map<String, OcelAttribute> vMap = new HashMap<>();
            for (String keyEntry: everyElementObject.keySet()) {
                if("ocel:vmap".equals(keyEntry) ) {
                    vMap = everyElementObject.getJSONObject(keyEntry)
                            .getInnerMap().entrySet().stream()
                            .map((x) -> new OcelAttribute(x.getKey(), new OcelElement(x.getValue())))
                            .collect(Collectors.toMap(OcelPart::getId, (x) -> x));
                } else if ("ocel:omap".equals(keyEntry)) {
                    eventBuilder = eventBuilder.omap(everyElementObject.getJSONArray("ocel:omap")
                            .toJavaList(String.class));
                } else {
                    vMap.put(keyEntry, new OcelAttribute(key, new OcelElement(everyElementObject.get(keyEntry))));
                }
            }
            eventBuilder.vmap(vMap);
            parsedEvents.add(eventBuilder.build());
//            for (Map.Entry<String, Object> eventElmt : eventElementMap.entrySet()) {
//                String keyEvent = eventElmt.getKey();
//                String valueEvent = eventElmt.getValue().toString();
//                if("ocel:vmap".equals(keyEvent) ) {
//                    Map<String, Object> vMap = JSON.parseObject(valueEvent, new TypeReference<>() {});
//                    Map<String, Object> vTmpMap = new HashMap<>(vMap);
//                    tmpMap.put(keyEvent,vTmpMap);
//                }else if("ocel:omap".equals(keyEvent) ) {
//                    List<String> tmpList = new ArrayList<>((Collection) eventElmt.getValue());
//                    tmpMap.put(keyEvent,tmpList);
//                } else {
//                    tmpMap.put(eventElmt.getKey(),eventElmt.getValue());
//                }
//            }
        }
        Collections.sort(parsedEvents);
        Map<String, OcelEvent> eventMap = new LinkedHashMap<>();
        for (OcelEvent event : parsedEvents) {
            eventMap.put(event.getId(), event);
        }
        logBuilder.events(eventMap);

        // save the objects element
        Map<String, Object> JSONobjectsMap = JSON.parseObject(objects, new TypeReference<>() {});
        Map<String, Map<String, Object>> allObjectsMap = new HashMap<>();
        Map<String, OcelObject> objectsMap = new HashMap<>();

        for (Map.Entry<String, Object> map : JSONobjectsMap.entrySet()){
            String key = map.getKey();
            Object value = map.getValue();

            Map<String, List<String>> ojbectElementMap = JSON.parseObject(value.toString(),  new TypeReference<>() {});
            JSONObject objectElement = JSON.parseObject(value.toString());
            OcelObject.OcelObjectBuilder objectBuilder = OcelObject.builder().id(key)
                    .type(objectElement.getString("ocel:type"));
            Map<String, OcelAttribute> vMap = new HashMap<>();
            for (String keyEntry: objectElement.keySet()) {
                if("ocel:ovmap".equals(keyEntry) ) {
                    vMap = objectElement.getJSONObject(keyEntry)
                            .getInnerMap().entrySet().stream()
                            .map((x) -> new OcelAttribute(x.getKey(), new OcelElement(x.getValue())))
                            .collect(Collectors.toMap(OcelPart::getId, (x) -> x));
                }
                else {
                    vMap.put(keyEntry, new OcelAttribute(key, new OcelElement(objectElement.get(keyEntry))));
                }
            }
            objectBuilder.ovmap(vMap);
            objectsMap.put(key, objectBuilder.build());
//            for (Map.Entry<String, List<String>> objectElmt : ojbectElementMap.entrySet()) {
//                String keyObject = objectElmt.getKey();
//                Object valueObject = objectElmt.getValue();
//                if("ocel:ovmap".equals(keyObject) ) {
//                    Map<?,?> ovMap = JSON.parseObject(valueObject.toString(), Map.class);
//                    Map<Object, Object> ovTmpMap = new HashMap<>();
//                    for (Map.Entry<?, ?> ovmapElment : ovMap.entrySet()) {
//                        ovTmpMap.put(((Map.Entry)ovmapElment).getKey(), ovmapElment.getValue());
//                    }
//                    tmpMap.put(keyObject,ovTmpMap);
//                    allObjectsMap.put(key,tmpMap);
//                }else {
//                    tmpMap.put(objectElmt.getKey(),objectElmt.getValue());
//                    allObjectsMap.put(key,tmpMap);
//                }
//            }
        }
        logBuilder.objects(objectsMap);
        return logBuilder.build();
    }
}
