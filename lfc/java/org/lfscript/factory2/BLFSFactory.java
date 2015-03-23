/*
 * - BLFSFactory.java -
 *
 * Copyright (c) 2012-2014 Marcel van den Boer
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

import java.io.IOException; /* FIXME: Factory should not perform I/O */

import nl.marcelweb.util.Pair;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;

public class BLFSFactory extends XMLFactory {

    @Override
    public void configureBlacklist(final Set<String> blacklist) {

        /* Custom scripts */
        blacklist.add("general/sysutils/udev.xml"); // No MD5
        blacklist.add("general/prog/perl-modules.xml"); // Multiple packages

        /* FIXME's */
        blacklist.add("postlfs/security/cacerts.xml"); // No MD5
        blacklist.add("general/prog/icedtea6.xml"); // No primary source

        blacklist.add("general/prog/php.xml"); // No primary source
        blacklist.add("general/sysutils/bluez.xml"); // heredoc error
    }

    public BLFSFactory(final String book, final String revision) {
        super(book, revision);
    }

    @Override
    protected Script parseXML(final String pack, final String xml)
            throws IOException {

        final Script script = super.parseXML(pack, xml);

        if (script != null) {
            this.parseBlfsSources(xml, script);
            this.parseBlfsDependencies(xml, script);
        }

        return script;
    }

    private Script lastScriptParsed = null;

    @Override
    protected void parseBuild(final String path, final String section,
            final Script script) {

        /* Do not allow muliple 'installation' sections in BLFS. */
        if (script == this.lastScriptParsed) {
            return;
        }
        this.lastScriptParsed = script;

        /* First, check which commands must be run as root */
        final Set<String> asRoot = new HashSet<String>();
        final List<Pair<String, String>> asRootRaw
                = SemiXML.innerXML(section, "screen",
                        " role=\"root\"",
                        " role='root'");
        for (final Pair<String, String> command : asRootRaw) {
            asRoot.add(SemiXML.asText(command.getA(), this.getEntities()));
        }

        /* Then, get all commands and check them against the root commands. */
        final List<Pair<String, String>> allCommands = SemiXML.innerXML(
                section, "userinput");
        for (final Pair<String, String> rawCommand : allCommands) {
            String command = SemiXML.asText(rawCommand.getA(),
                    this.getEntities());

            final List<String> finalCommands = new ArrayList<String>();
            /* Parse new type of BLFS X window system multibuilds */
            if (script.getName().indexOf("x7") == 0) {

                // Remove commands that open and close an unneccessary subshell
                if (command.equals("bash -e") || command.equals("exit")) {
                    continue;
                }

                // Remove loop logic from installation commands
                if (command.indexOf("for package in $(grep -v '^#' ../") == 0) {
                    final String delim = "pushd $packagedir";
                    final String endDelim = "popd";
                    command = command.substring(command.indexOf(delim) + delim.length());
                    command = command.substring(0, command.lastIndexOf(endDelim));

                    // Rewrite references to current working directory
                    command = command
                            .replace("$packagedir", "$(basename \"$PWD\")");

                    // Determine smallest indentation
                    int smallestIndent = 80;
                    final String[] indentedCommands = command.split("\n");
                    for (final String indentedCommand : indentedCommands) {
                        for (int i = 0; i < indentedCommand.length(); i++) {
                            if (indentedCommand.charAt(i) != ' ') {
                                smallestIndent = Math.min(smallestIndent, i);
                                break;
                            }
                        }
                    }

                    // Remove white space around commands
                    final StringBuilder newCommand = new StringBuilder();
                    for (int j = 0; j < indentedCommands.length; j++) {
                        final String indentedCommand = indentedCommands[j];

                        if (indentedCommand.trim().length() == 0) {
                            // Remove leading and trailing newlines
                            if (j == 0 || j == indentedCommands.length - 1) {
                                continue;
                            }

                            newCommand.append(indentedCommand.trim());
                        } else {
                            // Remove indentation
                            newCommand.append(
                                    indentedCommand.substring(smallestIndent));
                        }

                        newCommand.append("\n");
                    }
                    command = newCommand.toString().trim();
                }

                // Split command in compilation and installation
                StringBuilder compile = new StringBuilder();
                final String[] lines = command.split("\n");
                for (final String line : lines) {
                    if (line.indexOf("as_root") > -1) {
                        if (compile.toString().length() > 0) {
                            finalCommands.add(compile.toString().trim());
                            compile = new StringBuilder();
                        }

                        asRoot.add(line);
                        finalCommands.add(line);
                    } else {
                        compile.append(line).append("\n");
                    }
                }
                if (compile.toString().length() > 0) {
                    finalCommands.add(compile.toString().trim());
                    compile = new StringBuilder();
                }

            } else {
                finalCommands.add(command);
            }

            /* Add the command to the script. */
            for (final String finalCommand : finalCommands) {
                if (asRoot.contains(finalCommand)) {
                    script.putInstallation(finalCommand);
                } else {
                    script.putCompilation(finalCommand);
                }
            }
        }
    }

    private void parseBlfsSources(final String xml, final Script script)
            throws IOException {
        /* BLFS: Add primary source */
        String iNam = script.getName();
        String md5;

        if (iNam.equals("giflib")) { /* FIXME: package specific */
            md5 = this.getEntities().get("&giflib-http-md5sum;");
        } else if (iNam.equals("gnucash")) { /* FIXME: package specific */
            md5 = this.getEntities().get("&gnucash-src-md5sum;");
        } else if (iNam.equals("udev-extras")) { /* FIXME: package specific */
            md5 = "da8083b30b44177445b21e8299af23a1"; //20140712: eudev-1.9.tar.gz
        } else {
            md5 = this.getEntities().get('&' + iNam + "-md5sum;");
        }

        if (md5 == null && xml.indexOf("-size;") > -1) {
            /* Alternative internal name */
            iNam = xml.substring(0, xml.indexOf("-size;"));
            iNam = iNam.substring(iNam.lastIndexOf('&') + 1);

            md5 = this.getEntities().get('&' + iNam + "-md5sum;");
        }
        if (md5 == null) {
            md5 = "___PRIMARY_MD5_NOT_FOUND___";
            //throw new RuntimeException("Primary MD5 for " + script.getName()
            //        + " not found.");
        }

        String src = this.getEntities().get('&' + iNam + "-download-http;");
        if (iNam.equals("udev-extras")) {
            src = "http://dev.gentoo.org/~blueness/eudev/eudev-1.9.tar.gz"; //20140712: eudev-1.9.tar.gz
        } else if (src == null || src.trim().length() == 0) {
            src = this.getEntities().get('&' + iNam + "-download-ftp;");
        }
        if (src == null) {
            src = "___PRIMARY_SOURCE_NOT_FOUND___";
//            throw new RuntimeException("Primary source for " + script.getName()
//                    + " not found.");
        }
        script.setPrimarySource(src.trim(), md5.trim());


        /* BLFS: Xorg */
        if (script.getName().startsWith("x7")
                && !script.getName().startsWith("x7driver")) {
            if (xml.split("<literal>").length > 1) {
                script.setPrimarySource(null, null);

                final String[] sources = SemiXML.asText(
                        xml.split("<literal>")[1].split("</literal>")[0].trim(),
                        this.getEntities()).split("\n");

                for (final String line : sources) {
                    final String[] source = line.split("  ");
                    script.addSource(src + source[1], source[0]);
                }

                script.setMultiBuild(true);
            }
        }


        /* BLFS: Add secondary sources */
        final Set<String> search = script.usedFiles();
        final List<String> bSrcs = SemiXML.innerParameter(xml, "ulink", "url");
        for (String source : bSrcs) {
            source = SemiXML.asText(source, this.getEntities());

            if (source.lastIndexOf('/') == -1) {
                continue;
            }

            final String bsname = source.substring(source.lastIndexOf('/') + 1);

            if (search.contains(bsname)) {
                script.addSource(SemiXML.asText(
                        source, this.getEntities()), null);
                search.remove(bsname);
            }
        }
        if (search.contains("blfs-bootscripts")) {
            search.remove("blfs-bootscripts");
            script.addSource(
                    this.getEntities().get("&blfs-bootscripts-download;"),
                    null);
        }
        if (!search.isEmpty()) {
            System.err.println("WARNING: Sources missing in '"
                    + script.getName() + "': " + search);
        }
    }

    private void parseBlfsDependencies(final String xml, final Script script) {
        final Map<String, List<String>> depBlackList
                = Overrides.getInstance().getDependencyBlacklist();

        /*
         * Make sure the current script can be found as a dependency under it's
         * reference ID.
         */
        final List<String> refId = SemiXML.innerParameter(xml, "sect1", "id");

        if (refId.size() > 0) {
            script.addReferenceID(refId.get(0));
        }

        /* Find sections that contain REQUIRED dependencies */
        final String[] headers = xml.split("<bridgehead renderas=\"sect");
        for (final String header : headers) {
            if (header.startsWith("4\">Required")
                    || header.startsWith("5\">Required")) {

                /* Find sections after the header that define dependencies */
                final List<Pair<String, String>> required = SemiXML.innerXML(
                        header, "para", " role=\"required\"");

                for (final Pair<String, String> para : required) {

                    /* Find the actual dependency reference tags */
                    final List<String> dependencies = SemiXML.innerParameter(
                            para.getA(), "xref", "linkend");

                    addingDependencies:
                    for (final String dep : dependencies) {
                        final List<String> blacklist = depBlackList.get(
                            script.getName());

                        if (blacklist != null) {
                            for (final String blacklisted : blacklist) {
                                if (dep.equals(blacklisted)) {
                                    continue addingDependencies;
                                }
                            }
                        }

                        script.addDependency(dep);
                    }
                }
            }
        }
    }
}

