package com.dotteam.onceuponatown.culture;

public class CorruptedCultureException extends RuntimeException{

    /**
     * An exception raised when an invalid culture loads
     * @param culture The culture detected as invalid
     * @param file The invalid file
     * @param element The element in the file that may be invalid
     */
    CorruptedCultureException(String culture, String file, String element, String message) {
        super("Once upon a town MOD : " + culture + " is corrupted. Affected file : " + file + ". Affected element in file : " + element + "." + ((message == null) ? "" : " " + message));
    }

    CorruptedCultureException(String culture, String file, String message) {
        super("Once upon a town MOD : " + culture + " is corrupted. Affected file : " + file + "." + ((message == null) ? "" : " " + message));
    }

    CorruptedCultureException(String culture, String message) {
        super("Once upon a town MOD : " + culture + " is corrupted." + ((message == null) ? "" : " " + message));
    }
}
