/*
 * onprom-annoeditor
 *
 * NavigationalAttribute.java
 *
 * Copyright (C) 2016-2019 Free University of Bozen-Bolzano
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

package it.unibz.inf.onprom.data;

import it.unibz.inf.onprom.interfaces.DiagramShape;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * Attribute supporting navigation
 * <p>
 * @author T. E. Kalayci on 17/11/16.
 */
@Getter
public class NavigationalAttribute {
    @Setter
    private Set<DiagramShape> path;
    private UMLClass umlClass;
    private Attribute attribute;
    @Setter
    private String filterClause;

    NavigationalAttribute() {
    }

    public NavigationalAttribute(UMLClass _cls, Attribute _attr) {
        this(null, _cls, _attr);
    }

    public NavigationalAttribute(Set<DiagramShape> _path, UMLClass _cls, Attribute _attr) {
        path = _path;
        umlClass = _cls;
        attribute = _attr;
    }

    void reset() {
        path = null;
        umlClass = null;
        attribute = null;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        if (attribute != null)
            stringBuilder.append(attribute.getName()).append(" in ");
        if (umlClass != null)
            stringBuilder.append(umlClass.toString());
        if (path != null && !path.isEmpty()) {
            stringBuilder.append(" [ ");
            for (DiagramShape node : path) {
                stringBuilder.append(node).append("\u25b7");
            }
            //remove last character
            stringBuilder.deleteCharAt(stringBuilder.length() - 1).append("]");
        }
        return stringBuilder.toString();
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof NavigationalAttribute) {
            NavigationalAttribute other = (NavigationalAttribute) object;
            try {
                return other.getAttribute().equals(getAttribute()) && other.getUmlClass().equals(getUmlClass());
            } catch (NullPointerException e) {
                //
            }
        }
        return super.equals(object);
    }

}