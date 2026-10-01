/*
 * onprom-logextractor
 *
 * SimpleXESFactory.java
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

package it.unibz.inf.onprom.logextractor.ocel;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import it.unibz.inf.pm.ocel.entity.OcelAttribute;
import it.unibz.inf.pm.ocel.entity.OcelElement;
import it.unibz.inf.pm.ocel.entity.OcelExtension;
import lombok.Getter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class OCELFactory {
    @Getter
    private Date date;
    private final Interner<String> interner = Interners.newWeakInterner();
    private boolean useInterner = true;
    SimpleDateFormat WITH_T = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    SimpleDateFormat WITHOUT_T = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    SimpleDateFormat ONLY_DATE = new SimpleDateFormat("yyyy-MM-dd");

    public OCELFactory() {
    }

    private String intern(String s) {
        return this.useInterner ? this.interner.intern(s) : s;
    }

    public void setDate(Date date) {
        if (date == null) {
            throw new NullPointerException("No null value allowed in timestamp attribute!");
        } else {
            this.date = date;
        }

    }


    public OcelAttribute createAttribute(String type, String key, String value) throws ParseException {
        if (type != null && key != null && value != null) {
            if (type.equalsIgnoreCase("timestamp")) {
                // we assume that the timestamp is in format yyyy-[m]m-[d]d hh:mm:ss[.f...].
                // The fractional seconds may be omitted. The leading zero for mm and dd may also be omitted.
                Date date;
                try {
                    date = WITH_T.parse(value);
                } catch (ParseException e0) {
                    try {
                        date = WITHOUT_T.parse(value);
                    } catch (ParseException e1) {
                        date = ONLY_DATE.parse(value);
                    }
                }
                //return createAttributeTimestamp(key, date);
                return new OcelAttribute(key, new OcelElement(date), type);
            } else {
                return new OcelAttribute(key, new OcelElement(value, type), type);
            }
        }
        return null;
    }
}
