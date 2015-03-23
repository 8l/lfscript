/*
 * - ArgumentParser.java -
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
import java.util.StringTokenizer;

public class ArgumentParser {

    /** Maps GNU style options to the equivalent UNIX style options */
    private static final String[] gnuArguments = new String[] {
        /* GNU: */                       /* UNIX: */
        "--drop-package-on-error",       "-A",
        "--select-default-system",       "-B",
        "--set-system-to",               "-b",
        "--clean-build",                 "-C",
        "--set-overhead-path-to",        "-I",
        "--set-install-path-to",         "-i",
        "--set-kernel-configuration-to", "-k",
        "--list-dependencies-only",      "-L",
        "--download-sources-only",       "-S",
        "--set-software-to-skip",        "-s",
        "--set-software-to-rebuild",     "-U",
        "--use-prebuilt-packages",       "-u",
        "--add-extra-software",          "-x",
    };

    /* Instance variables */
    private final String baseSystem;
    private final String overheadPath;
    private final String installPath;
    private final String kernelConfigPath;
    private final boolean dropPackageOnError;
    private final boolean cleanBuild;
    private final boolean listDependenciesOnly;
    private final boolean downloadSourcesOnly;
    private final boolean useBackups;
    private final Set<String> extraSoftware;
    private final Set<String> skippedSoftware;
    private final Set<String> rebuildSoftware;

    public ArgumentParser(final String... arguments)
            throws IllegalArgumentException {

        /* Initialize sets */
        this.extraSoftware = new TreeSet<String>();
        this.skippedSoftware = new TreeSet<String>();
        this.rebuildSoftware = new TreeSet<String>();

        /* Non-final variables to temporarily hold parsed option arguments */
        String localBaseSystem = null;
        String localOverheadPath = null;
        String localInstallPath = null;
        String localKernelConfigPath = null;
        boolean localDropPackageOnError = false;
        boolean localCleanBuild = false;
        boolean localListDependenciesOnly = false;
        boolean localDownloadSourcesOnly = false;
        boolean localUseBackups = false;

        /* Parse arguments */
        for (int i = 0; i < arguments.length; i++) {
            String argument = arguments[i];

            /* Translate GNU style options to UNIX options */
            for (int j = 0; j < ArgumentParser.gnuArguments.length; j += 2) {
                if (argument.equals(ArgumentParser.gnuArguments[j])) {
                    argument = ArgumentParser.gnuArguments[j + 1];
                }
            }

            /* Parse UNIX style options */
            StringTokenizer tokens = null;
            if (argument.indexOf('-') == 0 && argument.charAt(1) != '-') {
                for (int j = 1; j < argument.length(); j++) {
                    switch (argument.charAt(j)) {
                    case 'A':
                        localDropPackageOnError = true;
                        break;

                    case 'B':
                        /* Do not allow multiple invocations */
                        if (localBaseSystem != null) {
                            final String selection = "base system";
                            ArgumentParser.throwSelectionException(selection);
                        }

                        localBaseSystem = "";
                        break;

                    case 'b':
                        /* Do not allow multiple invocations */
                        if (localBaseSystem != null) {
                            final String selection = "base system";
                            ArgumentParser.throwSelectionException(selection);
                        }

                        /* Option requires argument */
                        i = ArgumentParser.ensureOptArg(arguments, i, j);

                        localBaseSystem = arguments[i];
                        break;

                    case 'C':
                        localCleanBuild = true;
                        break;

                    case 'I':
                        /* Do not allow multiple invocations */
                        if (localOverheadPath != null) {
                            final String selection = "overhead path";
                            ArgumentParser.throwSelectionException(selection);
                        }

                        /* Option requires argument */
                        i = ArgumentParser.ensureOptArg(arguments, i, j);

                        localOverheadPath = arguments[i];
                        break;

                    case 'i':
                        /* Do not allow multiple invocations */
                        if (localInstallPath != null) {
                            final String selection = "installation path";
                            ArgumentParser.throwSelectionException(selection);
                        }

                        /* Option requires argument */
                        i = ArgumentParser.ensureOptArg(arguments, i, j);

                        localInstallPath = arguments[i];
                        break;

                    case 'k':
                        /* Do not allow multiple invocations */
                        if (localKernelConfigPath != null) {
                            final String select = "kernel configuration file";
                            ArgumentParser.throwSelectionException(select);
                        }

                        /* Option requires argument */
                        i = ArgumentParser.ensureOptArg(arguments, i, j);

                        localKernelConfigPath = arguments[i];
                        break;

                    case 'L':
                        localListDependenciesOnly = true;
                        break;

                    case 'S':
                        localDownloadSourcesOnly = true;
                        break;

                    case 's':
                        /* Option requires argument */
                        i = ArgumentParser.ensureOptArg(arguments, i, j);

                        tokens = new StringTokenizer(arguments[i]);

                        while (tokens.hasMoreTokens()) {
                            String skip = tokens.nextToken();
                            String[] parts = skip.split("/");
                            String unq = parts[parts.length - 1];

                            this.extraSoftware.add(skip);
                            this.skippedSoftware.add(unq);
                        }

                        break;

                    case 'U':
                        /* Option requires argument */
                        i = ArgumentParser.ensureOptArg(arguments, i, j);

                        tokens = new StringTokenizer(arguments[i]);

                        while (tokens.hasMoreTokens()) {
                            String rebuild = tokens.nextToken();
                            String[] parts = rebuild.split("/");
                            String unq = parts[parts.length - 1];

                            this.extraSoftware.add(rebuild);
                            this.rebuildSoftware.add(unq);
                        }

                        break;

                    case 'u':
                        localUseBackups = true;
                        break;

                    case 'x':
                        /* Option requires argument */
                        i = ArgumentParser.ensureOptArg(arguments, i, j);

                        tokens = new StringTokenizer(arguments[i]);

                        while (tokens.hasMoreTokens()) {
                            this.extraSoftware.add(tokens.nextToken());
                        }

                        break;

                    default:
                        throw new IllegalArgumentException("Unknown option: -"
                                + argument.charAt(j));
                    }
                }
            } else {
                throw new IllegalArgumentException("Unknown option: "
                        + argument);
            }
        }

        /* Finalize arguments */
        this.baseSystem = localBaseSystem;
        this.overheadPath = localOverheadPath;
        this.installPath = localInstallPath;
        this.kernelConfigPath = localKernelConfigPath;
        this.dropPackageOnError = localDropPackageOnError;
        this.cleanBuild = localCleanBuild;
        this.listDependenciesOnly = localListDependenciesOnly;
        this.downloadSourcesOnly = localDownloadSourcesOnly;
        this.useBackups = localUseBackups;

        /* Validate (some of the) arguments */
        if (this.baseSystem == null) {
            throw new IllegalArgumentException("No base system was specified.");
        }

        if (this.cleanBuild) {
            if (this.installPath != null) {
                throw new IllegalArgumentException("You can not perform a"
                        + " clean build within an installation directory.");
            }

            // TODO: Make possible
            if (!this.rebuildSoftware.isEmpty()) {
                throw new IllegalArgumentException("You can not rebuild"
                        + " software while performing a clean build. This"
                        + " would result in the same package being rebuild over"
                        + ", and over, and over again...");
            }

            // TODO: Make possible
            if (this.overheadPath != null) {
                throw new IllegalArgumentException("You can not use an"
                        + " overhead path while performing a clean build.");
            }
        }

        // TODO: Make possible
        if (!this.useBackups && this.overheadPath != null) {
            throw new IllegalArgumentException("You can only use an overhead"
                    + " path if a precompiled toolchain is available. Add -u.");
        }
    }

    private static void throwSelectionException(String selection) {
        throw new IllegalArgumentException("You can select only one "
                + selection + ".");
    }

    private static int ensureOptArg(final String[] arguments,
            final int argIndex, final int optIndex) {

        final String argument = arguments[argIndex];
        final char option = argument.charAt(optIndex);

        if (optIndex != argument.length() - 1) {
            throw new IllegalArgumentException("Option '" + option + "' must"
                    + " be the last entry in an option group");
        } else if (argIndex >= arguments.length) {
            throw new IllegalArgumentException("Option '" + option + "'"
                    + " requires an argument");
        }

        return argIndex + 1;
    }

    /* Temp */
    public static void main(final String[] args) {
        new ArgumentParser(args);
    }
}

