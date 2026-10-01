/*
 * ocel
 *
 * Ocelxml.java
 *
 * Copyright (C) 2016-2022 Free University of Bozen-Bolzano
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

package it.unibz.inf.pm.ocel.importer;


import it.unibz.inf.pm.ocel.entity.*;
import it.unibz.inf.pm.ocel.util.DateFormatUtil;
import it.unibz.inf.pm.ocel.util.XmlUtil;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;

import java.io.File;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.*;

@Slf4j
public class Ocelxml {
    public static OcelLog apply(String input_path) throws DocumentException {
        XmlUtil xmlUtil = new XmlUtil();
        Element root = xmlUtil.read(new File(input_path));
        Document dom = xmlUtil.getDocument();

        HashMap<String, Object> logMap = new HashMap<>();
        OcelLog.OcelLogBuilder logBuilder = OcelLog.builder();

        // parse <global scope="event">
        Element globalEventNode = XmlUtil.parse(root, "scope", "event");
        Map<String, String> globalEventMap = new HashMap<>();
        List<Element> subGlobalEventNodes = globalEventNode.elements();
        for(Element emt : subGlobalEventNodes) {
            globalEventMap.put(emt.attribute("key").getValue(),emt.attribute("value").getValue());
        }
        logBuilder.globalEvents(globalEventMap);

        // parse <global scope="object">
        Element globalObjectNode = XmlUtil.parse(root, "scope", "object");
        HashMap<String, String> globalObjectMap = new HashMap<>();
        List<Element> subGlobalObjectNodes = globalObjectNode.elements();
        for(Element emt : subGlobalObjectNodes) {
            globalObjectMap.put(emt.attribute("key").getValue(),emt.attribute("value").getValue());
        }
        logBuilder.globalObjects(globalObjectMap);

        // parse <global scope="log">
        Element globalLogNode = XmlUtil.parse(root, "scope", "log");
        HashMap<String, OcelAttribute> globalLogMap = new HashMap<>();
        List<Element> subGlobalLogNodes = globalLogNode.elements();
        List<String> attributeValueList = new ArrayList<>();
        List<String> objTpyeValueList = new ArrayList<>();
        for (Element emt : subGlobalLogNodes) {
            String value = emt.attribute("key").getValue();
            List<Element> attributeNamesElements = emt.elements();
            switch (value) {
                case "attribute-names":
                    for (Element attrElm : attributeNamesElements) {
                        attributeValueList.add(attrElm.attribute("value").getValue());
                    }
                    break;
                case "object-types":
                    for (Element attrElm : attributeNamesElements) {
                        objTpyeValueList.add(attrElm.attribute("value").getValue());
                    }
                    break;
                case "version":
                    globalLogMap.put("ocel:version",
                            new OcelAttribute("ocel:version", new OcelElement(emt.attribute("value").getValue())));
                    break;
                case "ordering":
                    globalLogMap.put("ocel:ordering",
                            new OcelAttribute("ocel:ordering", new OcelElement(emt.attribute("value").getValue())));
                    break;
            }
        }
        globalLogMap.put("ocel:attribute-names", new OcelAttribute("ocel:attribute-names",
                new OcelElement(attributeValueList)));
        globalLogMap.put("ocel:object-types", new OcelAttribute("ocel:attribute-names",
                new OcelElement(objTpyeValueList)));
        logBuilder.globalLog(globalLogMap);

        //parse the elements of <event>
        List<Element> eventsNodes = dom.selectNodes("//event");
        HashMap<String, OcelEvent> eventMapInOne = new HashMap<>();
        String eventKeyName = "";
        for (Element entElm: eventsNodes) {
            List<Element> eventElmt = entElm.elements();

            OcelEvent.OcelEventBuilder eventBuilder = OcelEvent.builder();
            HashMap<String, Object> eventMap = new HashMap<>();
            for (Element event: eventElmt ) {
                String keyStr = event.attribute("key").getValue();
                if("id".equals(keyStr)) {
                    eventKeyName = event.attribute("value").getValue();
                    eventBuilder = eventBuilder.id(eventKeyName);
                }else if("timestamp".equals(keyStr)){
                    try {
                        eventBuilder = eventBuilder.timestamp(
                                        DateFormatUtil.dealDateFormatReverse(event.attribute("value").getValue()));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }else if("activity".equals(keyStr)){
                    eventBuilder = eventBuilder.activity(event.attribute("value").getValue());
                    eventMap.put("ocel:activity",event.attribute("value").getValue());
                }else if ("omap".equals(keyStr)) {
                    List<Element> omapElements = event.elements();
                    List<String> ompValueList = new ArrayList<>();
                    for (Element ompElm : omapElements) {
                        ompValueList.add(ompElm.attribute("value").getValue());
                    }
                    eventBuilder = eventBuilder.omap(ompValueList);
                } else if ("vmap".equals(keyStr)) {
                    HashMap<String, OcelAttribute> vmapMap = extractMapFromElement(event);
                    eventBuilder = eventBuilder.vmap(vmapMap);
                }
            }
            if (eventKeyName.isEmpty() || !eventMap.keySet().containsAll(List.of("ocel:activity", "ocel:timestamp"))) {
                if (!eventKeyName.isEmpty()) {
                    log.warn("For event {}, activity or timestamp is not present. Ignoring...",  eventKeyName);
                } else {
                    log.warn("Found event without ID! Ignoring...");
                }
            } else {
                eventMapInOne.put(eventKeyName, eventBuilder.build());
            }
        }
        logBuilder.events(eventMapInOne);

        //parse the elements of <event>
        List<Element> objectsNodes = dom.selectNodes("//object");
        HashMap<String, OcelObject> objectMapInOne = new HashMap<>();
        for (Element objElm: objectsNodes) {
            String objectKeyName = "";
            List<Element> objElmt = objElm.elements();
            OcelObject.OcelObjectBuilder objectBuilder = OcelObject.builder();
            for (Element obj: objElmt ) {
                String keyStr = obj.attribute("key").getValue();
                if("id".equals(keyStr)) {
                    objectBuilder = objectBuilder.id(obj.attribute("value").getValue());
                }else if ("type".equals(keyStr)) {
                    objectBuilder = objectBuilder.type(obj.attribute("value").getValue());
                } else if ("ovmap".equals(keyStr)) {
                    objectBuilder = objectBuilder.ovmap(extractMapFromElement(obj));
                }
            }
        }
        logBuilder.objects(objectMapInOne);
        return logBuilder.build();
    }

    private static HashMap<String, OcelAttribute> extractMapFromElement(Element event) {
        HashMap<String, OcelAttribute> vmapMap = new HashMap<>();
        List<Element> vmapElements = event.elements();
        for (Element vmpElm : vmapElements) {
            String id = vmpElm.attribute("key").getValue();
            OcelAttribute.OcelAttributeBuilder attributeBuilder = OcelAttribute.builder().key(id);
            attributeBuilder = attributeBuilder.value(new OcelElement(parse_xml(vmpElm.attribute("value").getValue(),
                    "vmpElm.getName()").toString()));
            vmapMap.put(id, attributeBuilder.build());
        }
        return vmapMap;
    }


    public static Object parse_xml(String value, String tag_str_lower) {
        if (tag_str_lower.contains("float")) {
            return Float.parseFloat(value);
        } else if (tag_str_lower.contains("date")) {
            Date date = new Date(value);
            Timestamp timestamp = new Timestamp(date.getTime());
            return timestamp.toString();
        }
        return value;
    }

//    public static void main(String[] args) {
//        try {
//            System.out.println(Ocelxml.apply("minimal.xmlocel"));
//        } catch (DocumentException e) {
//            e.printStackTrace();
//        }
//    }


}
