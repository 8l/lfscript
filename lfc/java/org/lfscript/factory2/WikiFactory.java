/*
 * - WikiFactory.java -
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

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;

public class WikiFactory {
    final Map<String, Script> scripts = new HashMap<String, Script>();
    final Map<String, String> distro = new HashMap<String, String>();

    public WikiFactory(final String xmlFile) throws IOException {
        final String input = SemiXML.loadAscii(xmlFile);

        final Map<String, String> sections = new HashMap<String, String>();
        sections.put("lfscriptPackages", "extras");
        sections.put("userPackages", "contrib");

        for (final String sectionName : sections.keySet()) {
            final String section = WikiFactory.inner(input, sectionName);

            final String[] packages = section.split("<package>");
            for (int i = 1; i < packages.length; i++) {
                final String pkgDef = packages[i];

                /*
                 * <scriptname></scriptname>
                 */
                final String name = WikiFactory.inner(pkgDef, "scriptname");
                final Script script = new Script(name);
                script.addReferenceID(name);

                /*
                 * <md5></md5>
                 */
                final Map<String, String> md5s = new HashMap<String, String>();
                if (pkgDef.indexOf("md5") > -1) {
                    String md5raw = WikiFactory.inner(pkgDef, "md5");
                    md5raw = md5raw.replace("\n", " ");
                    md5raw = md5raw.replace("\t", " ");
                    while (md5raw.indexOf("  ") > -1) {
                        md5raw = md5raw.replace("  ", " ");
                    }
                    md5raw = md5raw.trim();

                    final String[] md5rawsplit = md5raw.split(" ");
                    for (int j = 0; j < md5rawsplit.length;) {
                        final String md5sum = md5rawsplit[j++];
                        final String basename = md5rawsplit[j++];

                        md5s.put(basename, md5sum);
                    }
                }

                /*
                 * <destdir></destdir>
                 */
                if (pkgDef.indexOf("<destdir>") > -1) {
                    Script.putDestdirFor(name,
                            WikiFactory.inner(pkgDef, "destdir"));
                }

                /*
                 * <sourcecode></sourcecode>
                 */
                final List<String> sources =
                        WikiFactory.inners(pkgDef, "sourcecode");
                if (sources.size() > 0) {
                    final String[] splitPrim = sources.get(0).split("/");
                    final String basePrim = splitPrim[splitPrim.length - 1];
                    script.setPrimarySource(sources.get(0), md5s.get(basePrim));

                    final Set<String> sourceBasenames = new HashSet<String>();
                    for (final String source : sources) {
                        final String[] split = source.split("/");
                        final String basename = split[split.length - 1];
                        if (!sourceBasenames.contains(basename)) {
                            sourceBasenames.add(basename);
                            script.addSource(source, md5s.get(basename));
                        }
                    }
                }

                /*
                 * <dependency></dependency>
                 * <dependency value="">
                 */
                final List<String> dependenciesA =
                        WikiFactory.inners(pkgDef, "dependency");
                for (final String dependency : dependenciesA) {
                    script.addDependency(dependency);
                }
                final List<String> dependenciesB =
                        SemiXML.innerParameter(pkgDef, "dependency", "value");
                for (final String dependency : dependenciesB) {
                    script.addDependency(dependency);
                }

                /*
                 * <preinst></preinst>
                 */
                final List<String> preinsts =
                        WikiFactory.inners(pkgDef, "preinst");
                for (final String command : preinsts) {
                    script.putPreInstallation(command);
                }

                /*
                 * <build></build>
                 * <build user="root"></build>
                 */
                final Set<String> asRoot = new HashSet<String>();
                final List<Pair<String,String>> asRootRaw =
                        SemiXML.innerXML(pkgDef, "build", "user=\"root\"");
                for (final Pair<String,String> command : asRootRaw) {
                    asRoot.add(command.getA());
                }

                final List<Pair<String,String>> builds =
                        SemiXML.innerXML(pkgDef, "build");
                for (final Pair<String,String> command : builds) {
                    if (asRoot.contains(command.getA())) {
                        script.putInstallation(command.getA());
                    } else {
                        script.putCompilation(command.getA());
                    }
                }

                /*
                 * <delaypostinst/>
                 * <postinst></postinst>
                 */
                final boolean delayPostinst =
                        pkgDef.indexOf("<delaypostinst/>") > -1;

                final List<String> postinsts =
                        WikiFactory.inners(pkgDef, "postinst");
                for (final String command : postinsts) {
                    script.putPostInstallation(command, delayPostinst);
                }

                this.scripts.put(name, script);
                this.distro.put(name, sections.get(sectionName));
            }
        }
    }

    public void saveAll(final String target) throws IOException {
        final Set<String> scriptKeys = this.scripts.keySet();
        for (final String script : scriptKeys) {
            final String output = this.scripts.get(script).getType4BScript(
                    true, null, false);

            final OutputStream o = new FileOutputStream(target + "/../" +
                    this.distro.get(script) + "/" + script);
            o.write(output.getBytes());
            o.close();
        }
    }

    private static String inner(final String contents, final String tag) {
        return contents.split("<" + tag + ">")[1].split("</" + tag + ">")[0];
    }

    private static List<String> inners(final String contents,
            final String tag) {
        final List<String> retVal = new ArrayList<String>();

        final String[] split = contents.split("<" + tag + ">");

        for (int i = 1; i < split.length; i++) {
            retVal.add(split[i].split("</" + tag + ">")[0]);
        }

        return retVal;
    }
}

