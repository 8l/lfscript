/*
 * - Overrides.java -
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

package org.lfscript.factory2;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Interfaces with an external list to provide manually verified MD5 checksums.
 */
public class Overrides {
    private Map<String, String> md5sums;
    private static Overrides instance;
    private Map<String, Object> distro;

    private Overrides() {
        this.md5sums = new HashMap<String, String>();

        try {
            File file = new File("md5sums.list");
            BufferedReader reader = new BufferedReader(new FileReader(file));

            String line = null;
            while((line = reader.readLine()) != null) {
                line = line.trim().replace("\t", " ");
                while(line.indexOf("  ") > -1) {
                    line = line.replace("  ", " ");
                }

                if(line.length() != 0 && line.charAt(0) != '#') {
                    this.md5sums.put(line.split(" ")[1], line.split(" ")[0]);
                }
            }
        } catch(Throwable t) {
            System.err.println("WARNING: No supplemental 'md5sums.list' found");
        }

        try {
            final String json = SemiXML.loadAscii("whitelists/distro.list");
            this.distro = JSON.decode(json);
        } catch (final IOException e) {
            System.err.println("WARNING: No supplemental 'distro.list' found");
            this.distro = new HashMap<String, Object>();
        }
    }

    /**
    * Provides an instance of <code>Overrides</code>.
    */
    public static Overrides getInstance() {
        if(Overrides.instance == null) {
            Overrides.instance = new Overrides();
        }

        return Overrides.instance;
    }

    /**
     * Returns a verified MD5 from 'md5sums.list', or <code>null</code> if the
     * original MD5 should be used.
     */
    public String getMD5(String file) {
        file = file.split("/")[file.split("/").length - 1];

        final String retVal = this.md5sums.get(file);
        if (retVal == null) {
            throw new RuntimeException("No MD5 for given file");
        }

        return retVal;
    }

    public List<String> getDependencies(final String name) {
        final Map<String, List<String>> dependencies
                = ((Map<String, Map<String, List<String>>>)
                        this.distro.get("dependencies")).get("required");

        List<String> list = dependencies.get(name);
        if (list == null) {
            list = new ArrayList<String>();
        }

        return list;
    }

    public Map<String, String> getRecommendedDependencies(final String name) {
        final Map<String, Map<String, String>> dependencies
                = ((Map<String, Map<String, Map<String, String>>>)
                        this.distro.get("dependencies")).get("recommended");

        Map<String, String> map = dependencies.get(name);
        if (map == null) {
            return Collections.emptyMap();
        } else {
            //return new TreeMap<String, String>(map); //Not supported by avian
            final Map<String, String> retVal = new TreeMap<String, String>();

            final Set<String> keys = map.keySet();
            for (final String key : keys) {
                retVal.put(key, map.get(key));
            }

            return retVal;
        }
    }

    public Map<String, List<String>> getDependencyBlacklist() {
        return ((Map<String, Map<String, List<String>>>)this.distro.get("dependencies")).get("blacklisted");
    }

    public Map<String, Map<String, String>> getCommandBlacklist() {
        return (Map<String, Map<String, String>>)this.distro.get("blacklistedCommands");
    }
}

