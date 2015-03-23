/*
 * - Script.java -
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

package org.lfscript.buildmgr;

import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Provides the interface to physical scripts. Additionally contains a few
 * utility methods to distinguish between <i>valid</i>, <i>unqualified</i> and
 * <i>qualified</i> names.
 * <p>
 * An instance of this class is immutable.
 *
 * @author Marcel van den Boer
 * @version 2011-04-04
 */
public class Script {
    private final Set<String> dependencies;
    private final boolean group;
    private final String name;

    /**
     * Constructs a new <code>Script</code> instance by loading the file
     * identified by the specified qualified name and <code>search</code>
     * directory.
     *
     * @throws IllegalArgumentException if <code>qlf</code> is not a
     *         qualified name.
     * @throws ScriptNotFoundException if no script named <code>qlf</code> is
     *         found in the <code>search</code> directory.
     * @throws ScriptParserException if the selected file could not be parsed
     *         correctly.
     */
    public Script(String qlf, String search, List<String> depLevels)
            throws ScriptNotFoundException, ScriptParserException {
        this.name = qlf;

        String contents = Script.loadTextFile(new File(search, qlf));

        if (!Script.isQualifiedName(qlf)) {
            throw new IllegalArgumentException(qlf);
        }

        if (contents == null) {
           throw new ScriptNotFoundException(qlf);
        }

        /* Create dependency list */
        this.dependencies = new TreeSet<String>();

        for (String depLevel : depLevels) {
            String[] occur = contents.split(
                    depLevel.toUpperCase()+"=\"");
            if (occur.length == 2) {
                String[] dependsArr = occur[1].split("\"");

                if (dependsArr != null && dependsArr.length > 0) {
                    String requires = dependsArr[0];

                    requires = requires.replace('\t', ' ');
                    requires = requires.replace('\n', ' ');
                    requires = requires.replace('\u000B', ' ');
                    requires = requires.replace('\f', ' ');
                    requires = requires.replace('\r', ' ');
                    while (requires.indexOf("  ") > -1) {
                        requires = requires.replaceFirst("  ", " ");
                    }

                    String[] deps = null;
                    if (requires.indexOf(" ") > -1) {
                        deps = requires.split(" ");
                    } else {
                        deps = new String[] { requires };
                    }

                    for (String dep : deps) {
                        if (Script.isUnqualifiedName(dep)) {
                            this.dependencies.add(dep);
                        } else {
                            throw new ScriptParserException(qlf, "Dependency '" +
                                    dep + "' is not an unqualified name.");
                        }
                    }

                } else {
                    throw new ScriptParserException(qlf,
                            "Unterminated REQUIRES variable.");
                }
            } else if (occur.length > 2) {
                throw new ScriptParserException(qlf,
                        "Multiple REQUIRES variables detected.");
            }
        }

        /* Parse tags */
        boolean group = false;
        String needle = "TAGS=\"";
        if(contents.indexOf(needle) > -1) {
            String ctags = contents.split(needle)[1].split("\"")[0];

            ctags = ctags.replace('\t', ' ');
            ctags = ctags.replace('\n', ' ');
            ctags = ctags.replace('\u000B', ' ');
            ctags = ctags.replace('\f', ' ');
            ctags = ctags.replace('\r', ' ');
            while (ctags.indexOf("  ") > -1) {
                ctags = ctags.replaceFirst("  ", " ");
            }

            String[] tags = null;
            if (ctags.indexOf(" ") > -1) {
                tags = ctags.split(" ");
            } else {
                tags = new String[] { ctags };
            }

            for (String tag : tags) {
                if (tag.toLowerCase().equals("group")) {
                    group = true;
                }
            }
        }
        this.group = group;
    }

    private static String loadTextFile(File file) {
        String contents = new String();
        String line = null;

        try {
            BufferedReader r = new BufferedReader(new FileReader(file));
            while((line = r.readLine()) != null) {
                contents += line + "\n";
            }
            r.close();
        } catch (IOException e) {
            return null;
        }

        return contents;
    }

    /** Returns a set of direct dependecies of this <code>Script</code>. */
    public Set<String> getDependencies() {
        Set<String> deps = new TreeSet<String>();

        deps.addAll(this.dependencies);

        return deps;
    }

    public boolean isGroup() {
        return this.group;
    }

    public String getQualifiedName() {
        return this.name;
    }

    public String getUnqualifiedName() {
        return Script.getUnqualifiedName(this.name);
    }

    /**
     * Returns whether or not the given <code>name</code> is a <i>valid</i>
     * identifier for <code>Script</code>s.
     * <p>
     * This method returns <code>true</code> if:
     * <ul>
     * <li><code>name</code> is not <code>null</code>, and</li>
     * <li><code>name</code> contains at most one slash (forward, '/'), however
     *     not as the first or last character, and</li>
     * <li><code>name</code> does not contain any backslashes ('\') or points
     *     ('.')</li>
     * </ul>
     * <p>
     * In all other cases, this method returns <code>false</code>.
     *
     * @see #isQualifiedName(String)
     * @see #isUnqualifiedName(String)
     */
    public static boolean isValidName(String name) {
        if (name == null) {
            return false;
        }

        int point = name.indexOf('.');
        int backslash = name.indexOf((char)0x5C);
        int firstslash = name.indexOf('/');
        int lastslash = name.lastIndexOf('/');

        if (point > -1 || backslash > -1 || firstslash != lastslash ||
                firstslash == 0 || firstslash == name.length() - 1) {
            return false;
        }

        return true;
    }

    /**
     * Returns whether or not the given name is a <i>qualified</i>
     * identifier for <code>Script</code>s.
     * <p>
     * This method returns <code>true</code> if:
     * <ul>
     * <li><code>qlf</code> is a <i>valid</i> name, and</li>
     * <li><code>qlf</code> contains a slash</li>
     * </ul>
     * <p>
     * In all other cases, this method returns <code>false</code>.
     *
     * @see #isUnqualifiedName(String)
     * @see #isValidName(String)
     */
    public static boolean isQualifiedName(String qlf) {
        return (Script.isValidName(qlf) && qlf.indexOf('/') > 0);
    }

    /**
     * Returns whether or not the given name is an <i>unqualified</i>
     * identifier for <code>Script</code>s.
     * <p>
     * This method returns <code>true</code> if:
     * <ul>
     * <li><code>unq</code> is a <i>valid</i> name, and</li>
     * <li><code>unq</code> does <b>not</b> contain a slash</li>
     * </ul>
     * <p>
     * In all other cases, this method returns <code>false</code>.
     *
     * @see #getUnqualifiedName(String)
     * @see #isQualifiedName(String)
     * @see #isValidName(String)
     */
    public static boolean isUnqualifiedName(String unq) {
        return (Script.isValidName(unq) && unq.indexOf('/') == -1);
    }

    /**
     * Returns the unqualified name (or basename) for the given
     * <code>name</code>. If <code>name</code> is already unqualified it is
     * returned as is.
     *
     * @throws IllegalArgumentException if the given <code>name</code> is not
     *         a valid identifier.
     *
     * @see #isUnqualifiedName(String)
     */
    public static String getUnqualifiedName(String name) {

        /* Make sure the name specified is valid */
        if (!Script.isValidName(name)) {
            throw new IllegalArgumentException(name);
        }

        /* Now simply return the portion after any slash */
        int delimPos = name.indexOf('/');

        if(delimPos == -1) {
            return name;
        } else {
            return name.substring(delimPos + 1, name.length());
        }
    }
}

