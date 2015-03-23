/*
 * - ScriptLoaderException.java -
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

import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * Thrown when a <code>ScriptLoader</code> has had one or more problems
 * regarding script loading.
 * <p>
 * An instance of this class has several <code>Set</code> fields, for several
 * types of problems. These allow a <code>ScriptLoader</code> to record
 * multiple problems, before throwing the exception.
 *
 * @author Marcel van den Boer
 * @version 2011-04-04
 */
public class ScriptLoaderException extends Exception {
    private static final long serialVersionUID = 0L;

    private Set<String> notFound;
    private Set<String> nonsense;
    private Map<String, Set<String>> multiple;
    private Set<ScriptParserException> contents;
    private Set<String[]> circular;

    /** Constructs a new <code>ScriptLoaderException</code> instance. */
    public ScriptLoaderException() {
        this.notFound = new TreeSet<String>();
        this.nonsense = new TreeSet<String>();
        this.multiple = new TreeMap<String, Set<String>>();
        this.contents = new HashSet<ScriptParserException>();
        this.circular = new HashSet<String[]>();
    }

    /**
     * Returns a <code>Set</code> for names of scripts which could not be
     * located.
     */
    public Set<String> getNotFoundNames() {
        return this.notFound;
    }

    /**
     * Returns a <code>Set</code> for names which contain illegal characters.
     */
    public Set<String> getNonValidNames() {
        return this.nonsense;
    }

    /**
     * Returns a <code>Map</code> containing entries for scripts which have
     * multiple implementations present in the file system.
     */
    public Map<String, Set<String>> getMultipleImplementations() {
        return this.multiple;
    }

    /**
     * Returns a <code>Set</code> of <code>ScriptParserException</code>s.
     */
    public Set<ScriptParserException> getParserExceptions() {
        return this.contents;
    }

    /**
     * Returns a <code>Set</code> of <code>String</code> arrays listing
     * circular dependencies.
     */
    public Set<String[]> getCircularDependencies() {
        return this.circular;
    }

    /** Returns whether or not problems have been reported to this instance. */
    public boolean hasEntries() {
        if (!this.notFound.isEmpty() || !this.nonsense.isEmpty() ||
                !this.multiple.isEmpty() || !this.contents.isEmpty() ||
                !this.circular.isEmpty()) {
            return true;
        }

        return false;
    }
}

