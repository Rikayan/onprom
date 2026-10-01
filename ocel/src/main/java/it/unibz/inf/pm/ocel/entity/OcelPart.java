package it.unibz.inf.pm.ocel.entity;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Getter;
import lombok.NonNull;

public abstract class OcelPart {
    @Getter
    @NonNull
    @JSONField(name = "ocel:id")
    String id;
    abstract String getDescription();
}
