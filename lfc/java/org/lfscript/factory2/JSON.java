/*
 * - JSON.java -
 *
 * Copyright (c) 2014 Marcel van den Boer
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

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class JSON {
    private int index = 0;

    private final String string;
    private final Map<String, Object> object;

    private JSON(final String json) {
        this.string = json.trim();
        this.index = 0;

        if (this.string.charAt(index) == '{') {
            this.object = this.compileObject();
        } else {
            throw new RuntimeException("Broken object");
        }

        if (this.index != this.string.length()) {
            throw new RuntimeException("Broken object");
        }
    }

    public Map<String, Object> getMap() {
        return this.object;
    }

    public static Map<String, Object> decode(String json) {
        return (new JSON(json)).getMap();
    }

    private Map<String, Object> compileObject() {
        this.index++; // skip '{'

        final Map<String, Object> object = new HashMap<String, Object>();

        boolean inComment = false;
        String nextKey = null;

        while (this.string.charAt(this.index) != '}') {
            final char currentCharacter = this.string.charAt(this.index);
            final char nextCharacter = this.string.charAt(this.index + 1);

            if (inComment) {
                if (currentCharacter == '*' && nextCharacter == '/') {
                    inComment = false;
                    this.index += 2;
                } else {
                    this.index++;
                }
            } else if (currentCharacter == '/' && nextCharacter == '*') {
                inComment = true;
                this.index += 2;
            } else if (currentCharacter == ' ' || currentCharacter == '\n'
                    || currentCharacter == ',' || currentCharacter == ':') {
                this.index++;
            } else if (currentCharacter == '"') {
                if (nextKey == null) {
                    nextKey = this.compileString();
                } else {
                    object.put(nextKey, this.compileString());
                    nextKey = null;
                }
            } else if (currentCharacter == '[') {
                object.put(nextKey, this.compileList());
                nextKey = null;
            } else if (currentCharacter == '{') {
                object.put(nextKey, this.compileObject());
                nextKey = null;
            } else {
                throw new RuntimeException("Unknown object char: "
                        + currentCharacter);
            }
        }

        this.index++; // skip '}'

        return object;
    }

    private String compileString() {
        this.index++; // skip first '"'

        final StringBuilder sb = new StringBuilder();

        while (this.string.charAt(this.index) != '"') {
            sb.append(this.string.charAt(this.index));
            this.index++;
        }

        this.index++; // skip last '"'

        return sb.toString();
    }

    private List compileList() {
        this.index++; // skip first '['

        boolean inComment = false;
        final List list = new ArrayList();

        while (this.string.charAt(this.index) != ']') {
            final char currentCharacter = this.string.charAt(this.index);
            final char nextCharacter = this.string.charAt(this.index + 1);

            if (inComment) {
                if (currentCharacter == '*' && nextCharacter == '/') {
                    inComment = false;
                    this.index += 2;
                } else {
                    this.index++;
                }
            } else if (currentCharacter == '/' && nextCharacter == '*') {
                inComment = true;
                this.index += 2;
            } else if (currentCharacter == ' ' || currentCharacter == '\n'
                    || currentCharacter == ',') {
                this.index++;
            } else if (currentCharacter == '"') {
                list.add(this.compileString());
            } else if (currentCharacter == '[') {
                list.add(this.compileList());
            } else if (currentCharacter == '{') {
                list.add(this.compileObject());
            } else {
                throw new RuntimeException("Unknown list char: "
                        + currentCharacter);
            }
        }

        this.index++; // skip last ']'

        return list;
    }
}

