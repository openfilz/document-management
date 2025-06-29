package org.openfilz.dms.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class MapEntry {
    private final String key;
    private final Object value;
}
