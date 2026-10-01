package it.unibz.inf.onprom.obdamapper;

import it.unibz.inf.ontop.spec.mapping.PrefixManager;
import org.javers.core.diff.custom.CustomValueComparator;

public class PrefixManagerComparator implements CustomValueComparator<PrefixManager> {

    @Override
    public boolean equals(PrefixManager left, PrefixManager right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }

        return left.getPrefixMap().equals(right.getPrefixMap());
    }

    @Override
    public String toString(PrefixManager value) {
        return value.getPrefixMap().toString();
    }
}