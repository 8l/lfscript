/*
 * - ScriptLoader.java -
 *
 * Copyright (c) 2011 Marcel van den Boer
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

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.TreeSet;
import java.util.HashSet;
import java.io.File;

/**
 * The parent class of all <code>ScriptLoader</code>s.
 *
 * @author Marcel van den Boer
 * @version 2011-04-04
 */
public abstract class ScriptLoader {
    private final String search;

    private final Map<String, String> registeredNames;
    private final Map<String, Script> registeredScripts;

    /** Constructs a new <code>ScriptLoader</code>. */
    public ScriptLoader(String search) {
        this.search = search;
        this.registeredNames = new HashMap<String, String>();
        this.registeredScripts = new HashMap<String, Script>();
    }

    /**
     * Returns the name of the directory which is used to search for
     * <code>Script</code>s.
     */
    public String getSearch() {
        return this.search;
    }

    /**
     * Returns the <code>Script</code> associated with the unqualified name
     * specified by <code>unq</code>.
     *
     * @throws IllegalArgumentException if no <code>Script</code> is loaded
     *         with the specified name.
     *
     * @see #registerScript(String, Script)
     */
    public Script getScript(String unq) {
        Script script = this.registeredScripts.get(unq);

        if (script == null) {
            throw new IllegalArgumentException(unq);
        }

        return script;
    }

    /**
     * Returns a new <code>Set</code> containing all <code>Script</code>s
     * loaded by this <code>ScriptLoader</code>.
     */
    public Set<Script> getScripts() {
        Set<Script> scripts = new HashSet<Script>();

        scripts.addAll(this.registeredScripts.values());

        return scripts;
    }

    /**
     * Returns the qualified name for the <code>Script</code> associated
     * with the unqualified name specified by <code>unq</code>.
     * <p>
     * @throws IllegalArgumentException if no <code>Script</code> has been
     *         loaded which has the specified name.
     *
     * @see #getExistingNames(String, String)
     * @see #registerName(String, String)
     */
    public String getRegisteredName(String unq) {
        String retVal = this.registeredNames.get(unq);

        if (retVal == null) {
            throw new IllegalArgumentException(unq);
        }

        return retVal;
    }

    /**
     * Pairs an unqualified name with a qualified name and stores them for
     * later use, overwriting an entry for <code>unq</code> if one was made
     * earlier.
     *
     * @throws IllegalArgumentException if <code>unq</code> is not an
     *         unqualified name or is <code>qlf</code> is not a qualified name.
     *
     * @see #getExistingNames(String, String)
     * @see #getRegisteredName(String)
     */
    protected void registerName(String unq, String qlf) {
        if (!Script.isUnqualifiedName(unq) || !Script.isQualifiedName(qlf)) {
            throw new IllegalArgumentException(unq + ", " + qlf);
        }

        this.registeredNames.put(unq, qlf);
    }

    /**
     * Pairs an unqualified name with a <code>Script</code> and stores them for
     * later use, overwriting an entry for <code>unq</code> if one was made
     * earlier.
     *
     * @throws IllegalArgumentException if <code>unq</code> is not an
     *         unqualified name.
     *
     * @see #getScript(String)
     */
    protected void registerScript(String unq, Script script) {
        if (!Script.isUnqualifiedName(unq)) {
            throw new IllegalArgumentException(unq);
        }

        this.registeredScripts.put(unq, script);
    }

    /**
     * Returns a <code>Set</code> with a qualified name for every file
     * named <code>unq</code> in the subdirectories of <code>search</code>.
     * <p>
     * If no file is found, an empty <code>Set</code> is returned.
     *
     * @throws IllegalArgumentException if <code>unq</code> is not unqualified.
     *
     * @see #getRegisteredName(String)
     * @see #registerName(String, String)
     */
    public static Set<String> getExistingNames(String unq, String search) {

        /* Make sure the name specified is unqualified */
        if (!Script.isUnqualifiedName(unq)) {
            throw new IllegalArgumentException(unq);
        }

        Set<String> list = new TreeSet<String>();

        /* Find possible scripts with this name */
        File[] dirs = (new File(search)).listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                File f = new File(dir, unq);
                if (f.isFile() && f.canRead()) {
                    list.add(dir.getName() + "/" + unq);
                }
            }
        }

        return list;
    }
}

