/*
 * - BuildBase.java -
 *
 * Copyright (c) 2012 Marcel van den Boer
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

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;

public class BuildBase {
    private final List<String> buildbase;
    private final Set<String> scripts;

    public BuildBase() {
        this.buildbase = new ArrayList<String>();
        this.scripts = new TreeSet<String>();
    }

    public void addCommand(final String command) {
        this.buildbase.add(command);
    }

    public void addBuild(final String name) {
        this.buildbase.add("BUILD " + name);
        this.scripts.add(name);
    }

    public void addSection(final String filename, final String xml,
            final Map<String, String> entities) {
        if (filename.endsWith("chapter06/revisedchroot.xml")) {
            this.buildbase.add("___NEW_SCOPE");
        } else if (filename.endsWith("chapter09/reboot.xml")) {
            this.buildbase.add("___BUILD_BEYOND_BEFORE");
        } else if (filename.endsWith("chapter05/changingowner.xml")) {
            this.buildbase.add("___RESUME_BACKUP");
        }

        final List<Pair<String, String>> commands
                = SemiXML.innerXML(xml, "userinput");

        if (commands.size() > 0) {
            final String title = SemiXML.innerXML(xml, "title").get(0).getA();

            final StringBuilder lines = new StringBuilder();
            for (int i = 0; i < title.length(); i++) {
                lines.append("-");
            }

            final StringBuilder sectHeader = new StringBuilder();
            sectHeader.append("# ").append(lines).append(" #\n");
            sectHeader.append("# ").append(title).append(" #\n");
            sectHeader.append("# ").append(lines).append(" #");

            this.buildbase.add(sectHeader.toString());
        }

        for (final Pair<String, String> command : commands) {
            this.buildbase.add(SemiXML.asText(command.getA(), entities));
        }
    }

    public void writeOut(final String target, final boolean withHeader,
            final Map<String, String> entities) throws IOException {
        final StringBuilder buffer = new StringBuilder();
        boolean buildGroup = false;

        /* Variables (after function identifiers) */
        buffer.append("BUILDUSER=\"lfs\"\n");
        buffer.append("ROOTVARIABLE=\"LFS\"\n");
        buffer.append("EXTERNAL_FOLDERS=\"/tools\"\n");
        buffer.append("CONTAINS=\"");

        StringBuilder contains = new StringBuilder();
        StringBuilder line = new StringBuilder();
        line.append("          ");

        for (final String script : this.scripts) {
            if (line.length() + script.length() < 79) {
                line.append(" ").append(script);
            } else {
                contains.append(line).append("\n");
                line = new StringBuilder();
                line.append("          ").append(script);
            }
        }
        contains.append(line).append("\"\n\n");
        buffer.append(contains.substring(11));

        buffer.append("# Note: This script is devided in several functions. ");
        buffer.append("Each function represents\n#       the scope of a ");
        buffer.append("shell. Whenever the shell environment changes ");
        buffer.append("(because\n#       of a 'su', 'source' or 'exec' ");
        buffer.append("command for example), LFScript will\n#       ");
        buffer.append("automatically transfer control to the next function.");
        buffer.append("\n\n");

        int scope = 0;
        String function;
        StringBuilder hashTags;

        /* Initial scope header */
        scope++;
        function = "buildbase" + scope + "() { # SHELL SCOPE #";
        hashTags = new StringBuilder();
        for (int i = 0; i < function.length(); i++) {
            hashTags.append("#");
        }
        buffer.append(hashTags).append("\n").append(function);
        buffer.append("\n").append(hashTags).append("\n\n");

        int resumeBackup = -1;
        int buildBeyond = -1;

        for (final String command : this.buildbase) {
            if (command.equals("umount -v $LFS")) {
                break;
            } else if (command.startsWith("BUILD ")) {
                if (!buildGroup) {
                    buffer.append("# (Compiling packages) #\n\n");
                }
                buildGroup = true;
                buffer.append(command).append("\n");
            } else {
                if (buildGroup) {
                    buffer.append("\n");
                }
                buildGroup = false;

                if (command.startsWith("___") || command.equals("logout")) {
                    /* Do not add (pseudo) command */
                } else {
                    buffer.append(command).append("\n\n");
                }
            }

            if (command.startsWith("___")
                    || command.startsWith("su ")
                    || command.startsWith("source ")
                    || command.startsWith("chroot ")
                    || command.startsWith("exec ")) {

                /* Add next scope header */
                scope++;
                function = "}; buildbase" + scope + "() { # SHELL SCOPE #";
                hashTags = new StringBuilder();
                for (int i = 0; i < function.length(); i++) {
                    hashTags.append("#");
                }
                buffer.append(hashTags).append("\n").append(function);
                buffer.append("\n").append(hashTags).append("\n\n");

                if (command.equals("___BUILD_BEYOND_BEFORE")) {
                    buildBeyond = scope - 1;
                } else if (command.equals("___RESUME_BACKUP")) {
                    resumeBackup = scope;
                }
            }
        }

        buffer.append("#################\n");
        buffer.append("} # END OF FILE #\n");
        buffer.append("#################\n\n");

        final OutputStream out = new FileOutputStream(target);

        out.write("#!/bin/bash\n\n".getBytes());

        /* File description header */
        if (withHeader) {
            out.write("# The instructions in this file are ".getBytes());
            out.write("extracted from\n# 'Linux From Scratch ".getBytes());
            out.write(entities.get("&milestone;").getBytes());
            out.write(("' (" + entities.get("&version;") + " / ").getBytes());
            out.write(entities.get("&scriptfactory-rev;").getBytes());
            out.write(").\n#\n# Linux From Scratch is released ".getBytes());
            out.write("under the MIT license.\n# Copyright (C) ".getBytes());
            out.write(entities.get("&copyrightdate;").getBytes());
            out.write(", Gerard Beekmans\n\n".getBytes());
        }

        out.write(("RESUMEBACKUP=\"" + resumeBackup + "\"\n").getBytes());
        out.write(("BUILDBEYOND=\"" + buildBeyond + "\"\n").getBytes());
        out.write(("LASTFUNCTION=\"" + scope + "\"\n").getBytes());

        out.write(buffer.toString().getBytes());
    }
}

