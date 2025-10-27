package org.dawnoftime.onceuponatown.culture;

public class CorruptedCultureException extends RuntimeException {
    public CorruptedCultureException(String cultureId, String message) {
        super("Culture [" + cultureId + "] is corrupted. " + message);
    }
}
