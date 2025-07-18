package org.openfilz.dms.utils;

public class SqlUtils {

    private static final String AND = " AND ";

    public static boolean isFirst(boolean first, StringBuilder sql) {
        if(!first) {
            sql.append(AND);
        } else {
            first = false;
        }
        return first;
    }

}
