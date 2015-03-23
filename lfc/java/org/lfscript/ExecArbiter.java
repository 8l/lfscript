/*
 * - ExecArbiter.java -
 *
 * Copyright (c) 2011-2012 Marcel van den Boer
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

package org.lfscript;

class ExecArbiter {
    public static void main(String... args) {
        if (args == null || args.length < 1) {
            ExecArbiter.invalidArg();
        }

        String[] nArgs = new String[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            nArgs[i - 1] = args[i];
        }

        if (args[0].equals("dep")) {
            DepHandler.main(nArgs);
        } else if (args[0].equals("factory2")) {
            org.lfscript.factory2.XMLFactory.main(nArgs);
        } else if (args[0].equals("iconfig")) {
            InteractiveConfiguration.main(nArgs);
        } else {
            ExecArbiter.invalidArg();
        }
    }

    private static void invalidArg() {
        System.err.println("What do you want me to do? [dep | factory | " +
                "factory2 | iconfig]");
        System.exit(1);
    }
}

