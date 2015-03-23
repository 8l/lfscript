/*
 * - XStartConfigurator.java -
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
import java.util.Collections;
import java.util.TreeSet;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;

/**
 * @author Marcel van den Boer
 * @version 2014-07-29
 */
public class XStartConfigurator {
    public static Set<String> getWindowManagers() {
        final Set<String> retVal = new TreeSet<String>();

        final File[] contents
                = (new File("/etc/alternatives/xinitrc")).listFiles();

        if (contents == null) {
            return Collections.emptySet();
        }

        for (final File file : contents) {
            final String name = file.getName().replace("_", " ");

            if (file.isFile() && file.getName().endsWith(".xinitrc")) {
                final int end = name.lastIndexOf(".xinitrc");

                retVal.add(name.substring(0, end));
            }
        }

        if (retVal.size() == 0) {
            return Collections.emptySet();
        } else {
            return retVal;
        }
    }

    private static void runCommand(final String command) throws IOException {
        final Process exec = Runtime.getRuntime().exec(command);

        try {
            exec.waitFor();
        } catch (InterruptedException e) {
            throw new IOException("Command interrupted '" + command + "'", e);
        }

        if (exec.exitValue() != 0) {
            throw new IOException("Exit status " + exec.exitValue()
                    + " for command '" + command + "'");
        }
    }

    public static void setGraphicalBoot(final boolean gb) throws IOException {
        final String cmd;

        if (gb) {
            cmd = "sed s/id:3:initdefault:/id:5:initdefault:/ -i /etc/inittab";
        } else {
            cmd = "sed s/id:5:initdefault:/id:3:initdefault:/ -i /etc/inittab";
        }

        XStartConfigurator.runCommand(cmd);
    }

    public static void setWindowManager(final String wm) throws IOException {
        if (!XStartConfigurator.getWindowManagers().contains(wm)) {
            throw new IOException("No such file: " + wm);
        }

        final String output = "DEFAULT_SESSION=\"" + wm + "\"";

        final OutputStream out = new FileOutputStream("/etc/xinitrc.conf");
        out.write(output.getBytes());
        out.close();
    }
}

