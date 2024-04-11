package com.dotteam.onceuponatown.culture;

public class CorruptedCultureException extends RuntimeException{
    CorruptedCultureException(String culture, String file, String element) {
        super("Once upon a town mod : " + culture + " is corrupted. Affected file : " + file + " at \"" + element + "\"");
    }
}
