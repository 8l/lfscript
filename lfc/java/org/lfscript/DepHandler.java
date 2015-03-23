/*
 * - DepHandler.java -
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

package org.lfscript;

import org.lfscript.buildmgr.*;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashSet;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 * This is a temporary class containing the entry point (main method) for
 * LFScript's revised dependency handler.
 */
class DepHandler {
    public static void main(String... args) {
        String runType = args[0];
        String scriptSearch = args[1];
        String packSearch = args[2];
        String excludeFile = args[3];

        final List<String> depLevels = new ArrayList<String>();
        Set<String> build = new TreeSet<String>();
        DynamicScriptLoader loader = null;

        boolean depLevelsDone = false;
        for (int i = 4; i < args.length; i++) {
            if (!depLevelsDone && args[i].equals("packs")) {
                depLevelsDone = true;
                continue;
            }

            if (depLevelsDone) {
                build.add(args[i]);
            } else {
                depLevels.add(args[i].toUpperCase());
            }
        }

        try {
            loader = new DynamicScriptLoader(build, scriptSearch, depLevels);
        } catch (IllegalArgumentException iae) {
            ScriptLoaderException sle = (ScriptLoaderException)iae.getCause();

            System.err.println(
                    "\nProplems with your selection have been detected:");

            /* Print conflicting selections */
            Set<String> keys = sle.getMultipleImplementations().keySet();
            for (String unq : keys) {
                System.err.println("* Conflicting selections of '" + unq +
                        "' have been made: " +
                        sle.getMultipleImplementations().get(unq));
            }

            /* Print invalid names */
            for (String invalid : sle.getNonValidNames()) {
                System.err.println("* The specified name '" + invalid + "' " +
                        "is not a valid script identifier.");
            }

            System.err.println("");

            System.exit(1);
        } catch (ScriptLoaderException sle) {

            System.err.println(
                    "\nThere was a problem while loading selected scripts:");

            /* Print conflicting selections */
            Set<String> keys = sle.getMultipleImplementations().keySet();
            for (String unq : keys) {
                System.err.println("* Multiple scripts have been found for '" +
                        unq + "': " +
                        sle.getMultipleImplementations().get(unq));
            }

            /* Print not found names */
            for (String invalid : sle.getNotFoundNames()) {
                System.err.println("* No script was found for '" + invalid +
                        "'.");
            }

            /* Print circular */
            for (String[] circ : sle.getCircularDependencies()) {
                System.err.print("* A chain of circular dependencies has " +
                        "been found: ");
                for (String dep : circ) {
                    System.err.print(dep + " -> ");
                }
                System.err.println(circ[0]);
            }

            /* Print parser errors */
            for (ScriptParserException spe : sle.getParserExceptions()) {
                System.err.println("* Parsing of script '" +
                        spe.getScriptName() + "' has failed: " +
                        spe.getMessage());
            }

            System.err.println("");

            System.exit(1);
        }

        Set<String> excludes = new HashSet<String>();
        try {
            FileReader fileReader = new FileReader(excludeFile);
            BufferedReader reader = new BufferedReader(fileReader);

            String line = null;
            while ((line = reader.readLine()) != null) {
                excludes.add(line);
            }
        } catch (IOException e) {
// No use in scaring people with a warning about an (as of yet) unimplemented
// feature...
//            System.err.println("[LFC] Warning - Exclude file not found:"
//                    + excludeFile);
            /* Ignore the exception */
        }

        List<String> buildOrder = null;
        if (excludes.isEmpty()) {
            buildOrder = loader.getBuildOrder();
        } else {
            buildOrder = loader.getRevisedBuildOrder(excludes);
        }

        if (runType.equals("rebuild")) {
            Set<String> toRebuild = loader.getDependentOn(excludes);
            for (String name : toRebuild) {
                System.out.println(name);
            }
        } else if (runType.equals("all")) {
            for (String fqn : buildOrder) {
                System.out.println(fqn);
            }
        } else if (runType.equals("next")) {
            String next = null;
            for (String fqn : buildOrder) {
                Script s = loader.getScript(Script.getUnqualifiedName(fqn));
                File f1 = new File(packSearch, fqn + ".txz");
                File f2 = new File(packSearch, fqn + ".tgz");
                if (f1.exists()) {
                    continue;
                } else if (f2.exists()) {
                    continue;
                } else if (s.isGroup()) {
                    continue;
                }

                next = Script.getUnqualifiedName(fqn);
                break;
            }

            if (next != null) {
                List<String> order = loader.getBuildOrder(next);
                for (String fqn : order) {
                    System.out.println(fqn);
                }
            }

        } else {
            System.err.println(
                "[LFC] First argument should be 'all', 'next' or 'rebuild'"
            );
            System.exit(1);
        }
    }
}

