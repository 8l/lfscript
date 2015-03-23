/*
 * - SemiXML.java -
 *
 * Copyright (c) 2011-2014 Marcel van den Boer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package org.lfscript.factory2;

import nl.marcelweb.util.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Collections;
//import java.text.ParseException; /* FIXME */

/**
 * A naive XML processor, designed to be able to load text files and extract
 * certain kinds of data from XML documents.
 * <p>
 * This class will not work properly with documents that contain markup
 * errors.
 */
public class SemiXML {

    /**
     * Thrown if an XML entity cannot be resolved in the
     * <code>SemiXML.asText</code> method.
     */
    public static class UnresolvedEntityException extends RuntimeException {
        public UnresolvedEntityException(final String message) {
            super(message);
        }
    };

    /**
     * Returns the parent directory of the given <code>path</code>.
     */
    public static String dirOf(final String path) {
        final StringBuilder sb = new StringBuilder();

        final String[] split = path.split("/");
        for (int i = 0; i < split.length - 1; i++) {
            sb.append(split[i]).append("/");
        }

        return sb.toString();
    }

    /**
     * Loads an ASCII text file.
     * <p>
     * NOTE: This method ensures that the last character of the returned
     * <code>String</code> always is a newline '\n'. If needed, it adds one.
     */
    public static String loadAscii(final String path) throws IOException {
        final InputStream input = new FileInputStream(path);

        final StringBuilder sb = new StringBuilder();

        int character;
        while ((character = input.read()) != -1) {
            if ((0x1F < character && character < 0x7F) || character == 0x09
                    || character == 0x0A || character == 0x0D) {
                sb.append((char)character);
            } else {
                /*
                throw new RuntimeException("Source not pure ASCII: Line "
                        + sb.toString().split("\n").length + " of "
                        + path);
                */
                System.out.println("WARNING: Source not pure ASCII.");
                System.out.println("         "
                        + sb.toString().split("\n").length + ": "
                        + path);
            }
        }

        input.close();

        /* Ensure that the last character in the return value is a newline */
        if (sb.charAt(sb.length() - 1) != '\n') {
            sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * Loads an ASCII text file and returns it with all XML comments removed.
     */
    public static String loadXml(final String path) throws IOException {
        return SemiXML.removeAll(SemiXML.loadAscii(path), "<!--", "-->");
    }

    /**
     * Loads an ASCII text file and returns it as an array of lines, with all
     * comments (preceeded by a '#' character) removed.
     */
    private static String[] loadLines(final String path) throws IOException {
        return SemiXML.removeAll(SemiXML.loadAscii(path), "#", "\n")
                .split("\n");
    }

    /**
     * Loads an ASCII text file and returns a <code>List</code> of trimmed,
     * non-empty lines, with all comments (preceeded by a '#' character)
     * removed.
     */
    public static List<String> loadList(final String path) throws IOException {
        /* Designed to load BLFS WGET lists */

        final List<String> retVal = new ArrayList<String>();

        final String[] lines = SemiXML.loadLines(path);

        for (String line : lines) {
            line = line.trim();

            if (line.length() > 0) {
                retVal.add(line);
            }
        }

        return retVal;
    }

    /**
     * Loads an ASCII text file and returns a <code>Map</code> where the keys
     * are the second item, and the values the first item of each line. Items
     * are seperated by spaces or tabs and may be indented.
     * <p>
     * All comments (preceeded by a '#' character) are removed before parsing
     * the file.
     */
    public static Map<String, String> loadReversedMap(final String path)
            throws IOException { //, ParseException { /* FIXME */
        /* Designed to load MD5 checksum lists */

        final Map<String, String> retVal = new HashMap<String, String>();

        final String[] lines = SemiXML.loadLines(path);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace("\t", " ");
            while (line.indexOf("  ") > -1) {
                line = line.replace("  ", " ");
            }
            line = line.trim();

            if (line.length() == 0) {
                continue;
            }

            final String[] pair = line.split(" ");
            if (pair.length != 2) {
                throw new RuntimeException("Item count is not two"); /* FIXME */
//                throw new ParseException("Item count is not two", i);
            }

            retVal.put(pair[1], pair[0]);
        }

        return retVal;
    }

    /**
     * Returns a list of parameter contents for all XML tags of a certain
     * type, which have a certain parameter defined.
     */
    public static List<String> innerParameter(final String contents,
            final String tag, final String parameter) {

        /* Designed to load <xi:include href="???"/> and return the ??? */

        final List<String> retVal = new ArrayList<String>();

        final String[] split = contents.split("<" + tag);
        if (split.length < 2) {
            return Collections.emptyList();
        }

        for (int i = 1; i < split.length; i++) {
            String part = split[i];
            part = part.substring(0, part.indexOf(">"));

            /* Does a whitespace follow the tag? If not, search on! */
            if (part.length() == 0 || part.charAt(0) > 0x20) {
                continue;
            }

            final String fullParam = " " + parameter + "=";
            final int valStart = part.indexOf(fullParam) + fullParam.length();

            if (valStart != -1) {

                part = part.substring(valStart);

                final int firstQuot = part.indexOf('"');
                final int firstApos = part.indexOf('\'');

                final char delim;
                final int dStart;
                if (firstQuot == -1
                        || (firstApos != -1 && firstApos < firstQuot)) {
                    delim = '\'';
                    dStart = firstApos + 1;
                } else if (firstApos == -1 || firstQuot < firstApos) {
                    delim = '"';
                    dStart = firstQuot + 1;
                } else {
                    throw new RuntimeException("Whoooooooooooops!");
                }

                try {
                    retVal.add(part.substring(
                            dStart, part.indexOf(delim, dStart)));
                } catch (final Throwable t) {
                    System.err.println("Delimeter error (XML syntax error?)");
                    System.err.println("---> " + delim);
                    System.err.println("---> " + dStart);
                    System.err.println("---> " + part.indexOf(delim, dStart));
                    System.err.println("---> " + part);
                    throw new RuntimeException(t);
                }
            }
        }

        return retVal;
    }

    /**
     * Returns the text between an XML start and end tag, which has some or
     * more parameter/value pairs defined.
     * <p>
     * For each tag found, text is added as a separate entry in the
     * <code>List</code>. The returned <code>List</code> is made up of
     * <code>Pair</code>s. The first entry in each <code>Pair</code> is the
     * found inner XML text. The second entry is the parameter the enclosing
     * tag had.
     */
    public static List<Pair<String, String>> innerXML(final String contents,
            final String tag, final String... parameters) {

        final String[] split = contents.split("<" + tag);
        if (split.length < 2 && !contents.startsWith("<" + tag)) {
            return Collections.emptyList();
        }

        final List<Pair<String, String>> parts
                = new ArrayList<Pair<String, String>>();

        for (int i = 1; i < split.length; i++) {
            final int tagEnd = split[i].indexOf(">");
            final int end = split[i].indexOf("</" + tag);

            if (parameters.length > 0) {
                for (final String param : parameters) {
                    if (split[i].substring(0, tagEnd).indexOf(param) != -1) {
                        parts.add(new Pair<String, String>(
                                split[i].substring(tagEnd + 1, end), param));
                    }
                }
            } else {
                parts.add(new Pair<String, String>(
                        split[i].substring(tagEnd + 1, end), null));
            }
        }

        return parts;
    }

    /**
     * Removes all XML tags from a <code>String</code> and replaces entity
     * variables with their actual value.
     * <p>
     * This method might throw an <code>UnresolvedEntityException</code> if
     * <code>contents</code> contains unknown entity variables.
     */
    public static String asText(final String contents,
            final Map<String, String> customEntities) {
        String text = contents;

        /* 1. Remove all XML tags */
        text = SemiXML.removeAll(text, "<", ">");

        /* 2a. Replace the XML built in entities  (1 of 2)*/
        text = text.replace("&amp;", "<!-- AMPERSAND -->");
        text = text.replace("&gt;", ">");
        text = text.replace("&lt;", "<");
        text = text.replace("&quot;", "\"");
        text = text.replace("&apos;", "'");

        /* 2b. Replace ASCII character entities */
        for (int i = 32; i < 127; i++) {
            if (text.indexOf("&#" + i + ";") > -1) {
                text = text.replace("&#" + i + ";", "" + (char)i);            
            }
        }

        /* 3. Replace custom XML entities */
        final Set<String> keys = customEntities.keySet();
        for (final String key : keys) {
            final String value = customEntities.get(key);
            text = text.replace(key, value);
        }

        /* 4. Verify that there are no more entities to resolve */
        final int nextAmp = text.indexOf('&');
        if (nextAmp != -1) {
            throw new UnresolvedEntityException("Unresolved entity: "
                    + text.substring(nextAmp, text.indexOf(';', nextAmp)));
        }

        /* 5. Replace the XML built in entities (2 of 2) */
        text = text.replace("<!-- AMPERSAND -->", "&");

        return text;
    }

    /**
     * Removes all text from <code>contents</code> between occurrences of the
     * given phrases <code>a</code> and <code>b</code>.
     */
    private static String removeAll(final String contents,
            final String a, final String b) {
        String text = contents;

        /* Forces the use of &gt; and &lt; (in stead of '>' and '<') in XML */
//        if (text.indexOf(a) > text.indexOf(b)) {
//            throw new RuntimeException("'" + b + "' before '" + a + "', or "
//                    + "rogue '" + a + "'");
//        }

        int start;
        while ((start = text.indexOf(a)) != -1) {
            final int stop = text.indexOf(b, start + a.length());

            if (stop == -1) {
                throw new RuntimeException("'" + a + "' is unclosed");
            }

            text = text.substring(0, start) + text.substring(stop + b.length());
        }

        /* Forces the use of &gt; (in stead of '>') in XML */
//        if (noRogues && text.indexOf(b) != -1) {
//            throw new RuntimeException("Rogue '" + b + "'");
//        }
        return text;
    }

    /* ----------------------- XML ENTITY PARSING ----------------------- */

    /**
     * Returns a <code>Map</code> with XML entity/value pairs. The values are
     * guaranteed to not hold variables, as they are resolved automatically.
     * <p>
     * This method might throw an <code>UnresolvedEntityException</code> if
     * <code>file</code> contains unknown entity variables.
     */
    public static Map<String, String> getEntitiesFrom(final String file,
            final String xml, final Map<String, String> entities)
                    throws IOException {
        final Map<String, String> newEntities = SemiXML.loadEntities(file, xml);

        /* Check for key overwrites (3/4) */
        final Set<String> checkKeys = newEntities.keySet();
        for (final String checkKey : checkKeys) {
            if (!checkKey.startsWith("&%") && entities.containsKey(checkKey)) {
                System.err.println(
                        "WARNING: Entity overwrite '" + checkKey + "' (3)");
            }
        }

        entities.putAll(newEntities);

        return SemiXML.resolveEntities(entities);
    }

    /**
     * Ensures that the values in an entity database do not contain entities
     * themselves.
     */
    private static Map<String, String> resolveEntities(
                final Map<String, String> entities) {

        final Map<String, String> retVal = new HashMap<String, String>();

        final Set<String> keys = entities.keySet();
        for (final String key : keys) {
            String value = entities.get(key);

            int amp;
            while ((amp = value.indexOf('&')) != -1) {
                final int semi = value.indexOf(';', amp) + 1;
                final String entity = value.substring(amp, semi);
                final String entValue = entities.get(entity);

                if (entity.equals("&lt;")) {
                    value = value.replace(entity, "<"); /* FIXME */
                } else if (entity.equals("&gt;")) {
                    value = value.replace(entity, ">"); /* FIXME */
                } else if (entity.equals("&apos;")) {
                    value = value.replace(entity, "'"); /* FIXME */
                } else if (entity.equals("&quot;")) {
                    value = value.replace(entity, "\""); /* FIXME */
                } else if (entValue != null) {
                    value = value.replace(entity, entValue);
                } else {
                    throw new UnresolvedEntityException("Unresolved entity: "
                            + entity);
                }
            }
            retVal.put(key, value);
        }

        return retVal;
    }

private final static Set<String> loaded = new HashSet<String>();

    /**
     * Returns a <code>Map</code> with XML entity/value pairs. The values may
     * contain entity variables.
     */
    private static Map<String, String> loadEntities(final String entFile,
            final String xml) throws IOException {

        /* Load initial entities */
        final Map<String, String> entities = SemiXML.parseEntities(xml);

        /* Prepare database for recursive loading */
        final Map<String, String> newEntities = new HashMap<String, String>();

        /* Load any new entity definition files */
        final Set<String> keys = entities.keySet();
        for (final String key : keys) {
            if (key.startsWith("&%")) {
                final String nFile = SemiXML.dirOf(entFile) + entities.get(key);


final String xxx = nFile.substring(nFile.lastIndexOf('/'));
if (SemiXML.loaded.contains(xxx)) {
    continue;
} else {
    SemiXML.loaded.add(xxx);
}

                final Map<String, String> recursive
                        = SemiXML.loadEntities(nFile, SemiXML.loadXml(nFile));

                /* Check for key overwrites (1/4) */
                final Set<String> checkKeys = recursive.keySet();
                for (final String checkKey : checkKeys) {
                    if (newEntities.containsKey(checkKey)) {
                        System.err.println("WARNING: Entity overwrite '" +
                                checkKey + "' (1)");
                    }
                }

                newEntities.putAll(recursive);
            }
        }

        /* Check for key overwrites (2/4) */
        final Set<String> checkKeys = entities.keySet();
        for (final String checkKey : checkKeys) {
            if (newEntities.containsKey(checkKey)) {
                System.err.println(
                        "WARNING: Entity overwrite '" + checkKey + "' (2)");
            }
        }

        /* Add the additional loaded entities */
        newEntities.putAll(entities);

        return newEntities;
    }

    /**
     * Returns a map of all XML entity definitions.
     */
    private static Map<String, String> parseEntities(final String contents) {
        final String[] split = contents.split("<!ENTITY ");
        if (split.length < 2) {
            return Collections.emptyMap();
        }

        final Map<String, String> retVal = new HashMap<String, String>();

        for (int i = 1; i < split.length; i++) {
            final int valueStart = split[i].indexOf('"');
            final int valueEnd = split[i].indexOf('"', valueStart + 1);

            final String key = split[i].substring(0, valueStart).trim();
            final String value = split[i].substring(valueStart + 1, valueEnd);

            /* Check for key overwrites (4/4) */
            if (retVal.containsKey("&" + key + ";")) {
                System.err.println(
                        "WARNING: Entity overwrite '" + key + "' (4)");
            }

            retVal.put("&" + key + ";", value);
        }

        return retVal;
    }
}

