/*
 * - ScriptParserException.java -
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

/**
 * A <code>ScriptParserException</code> is thrown if there was a problem
 * parsing a particular script file.
 *
 * @author Marcel van den Boer
 * @version 2011-04-03
 */
public class ScriptParserException extends Exception {
    private static final long serialVersionUID = 0L;

    private final String name;

    /**
     * Constructs a new <code>ScriptParserException</code> with a detailed
     * <code>message</code> describing the nature of the problem in the script
     * identified with <code>qlf</code>.
     */
    public ScriptParserException(String qlf, String message) {
        super(message);
        this.name = qlf;
    }

    /**
     * Returns the qualified name of the script in which the exception was
     * thrown.
     */
    public String getScriptName() {
        return this.name;
    }
}

