package com.alkadev.annotations;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opening a properties file. Some editors (Notepad of Windows, saving as UTF-8) put three bytes, a byte order mark, in front of the first line; read as ISO-8859-1
 * they become the letters that stand before the first key, and that key is lost without a word. The engine and the Studio open files here, so both skip the mark.
 */
public final class PropertyFiles {
    /** The mark as the characters it becomes when a file is read as ISO-8859-1. */
    public static final String MARK_AS_TEXT = "\u00EF\u00BB\u00BF";

    private PropertyFiles() {
    }

    /** The content of the file, without a byte order mark at its start. */
    public static InputStream open(Path file) throws IOException {
        BufferedInputStream in = new BufferedInputStream(Files.newInputStream(file));
        in.mark(3);
        byte[] first = in.readNBytes(3);
        if (!(first.length == 3 && first[0] == (byte) 0xEF && first[1] == (byte) 0xBB && first[2] == (byte) 0xBF)) in.reset();
        return in;
    }

    /** Text read as ISO-8859-1, without the mark. */
    public static String withoutMark(String text) {
        return text.startsWith(MARK_AS_TEXT) ? text.substring(MARK_AS_TEXT.length()) : text;
    }
}
