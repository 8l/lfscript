/*
 * - DynamicScriptLoader.java -
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

import java.util.List;
import java.util.ArrayList;

import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;

import java.util.Stack;

/**
 * Dynamically loads and keeps selected <code>Script</code>s and their
 * dependencies.
 * <p>
 * Use this class to load scripts that extends the basic Linux system (from
 * for example <i>BLFS</i>).
 * <p>
 * Objects of this class are immutable. Once created it cannot be modified.
 *
 * <h3>Example:</h3>
 * The following code prints a list of all dependencies (in correct building
 * order) of 'xorg', provided that scripts for 'xorg' and it's dependencies are
 * available in the './scripts' directory.
 * <p>
 *<pre>{@code
 *Set<String> build = new HashSet<String>();
 *build.add("xorg");
 *
 *try {
 *
 *    DynamicScriptLoader load = new DynamicScriptLoader(build, "./scripts");
 *    List<String> list = load.getBuildOrder();
 *
 *    System.out.println("Listing all dependencies for " + build + ": ");
 *
 *    int i = 0;
 *    for (String entry : list) {
 *        System.out.println("" + ++i + ". " + entry);
 *    }
 *
 *    System.out.println("Done");
 *} catch (IllegalArgumentException iae) {
 *
 *    // Executed if one or more entries in 'build' are not valid names
 *    // Note that a ScriptLoaderException with details is available through
 *    // 'iae.getCause()'.
 *    ...
 *
 *} catch (ScriptLoaderException sle) {
 *
 *    // Executed if there is a problem resolving or loading scripts
 *    ...
 *}}
 *</pre>
 *
 * @author Marcel van den Boer
 * @version 2011-04-04
 */
public class DynamicScriptLoader extends ScriptLoader {
    private final List<String> buildOrder;

    /**
     * Constructs a new <code>DynamicScriptLoader</code> and attempts to
     * construct <code>Script</code>s for the given <code>names</code> and
     * any required dependencies.
     * <p>
     * Problems that occur while processing <code>names</code> and
     * constructing <code>Script</code>s are stored in a
     * <code>ScriptLoaderException</code> which is subsequently thrown.
     *
     * @throws IllegalArgumentException if conflicting entries are present in
     *         <code>names</code> or if an entry is not a valid identifier.
     *         Use <code>getCause()</code> on this exception to get a
     *         <code>ScriptLoaderException</code> instance containing lists
     *         of names that caused this exception.
     *
     * @throws ScriptLoaderException if there are problems constructing
     *         <code>Script</code>s.
     */
    public DynamicScriptLoader(Set<String> names, String search,
            List<String> depLevels) throws ScriptLoaderException {

        super(search);

        /* Initialize the fields of this object. */
        this.buildOrder = new ArrayList<String>();

        /*
         * Prepare an exception which will be used as a data structure.
         * If a value is added to one of the contained sets, the exception
         * should be thrown.
         */
        ScriptLoaderException exception = new ScriptLoaderException();

        /* First, validate the initially given names */
        Set<String> unqualified = new TreeSet<String>();
        for (String name : names) {
            if (!Script.isValidName(name)) {

                /* Catch nonsensical input */
                exception.getNonValidNames().add(name);

            } else if (Script.isQualifiedName(name)) {
                String qlf = name;
                String unq = Script.getUnqualifiedName(name);

                try {
                    String reg = super.getRegisteredName(unq);

                    /*
                     * The name has been specified before. If it is not equal
                     * to the already registered name, add the names to a list
                     * of invalid duplicates.
                     */
                    if (!reg.equals(qlf)) {
                        Set<String> multi = null;
                        multi = exception.getMultipleImplementations().get(unq);

                        if (multi == null) {
                            multi = new TreeSet<String>();
                        }

                        multi.add(reg);
                        multi.add(qlf);

                        exception.getMultipleImplementations().put(unq, multi);
                    }
                } catch (IllegalArgumentException e) {

                  /*
                   * Only register the name now, if a script exists it will
                   * be loaded later.
                   */
                  super.registerName(unq, qlf);
                  unqualified.add(unq);
                }

            } else {

                /*
                 * Add valid unqualified names to the Set of names to load
                 * scripts for.
                 */
                unqualified.add(name);
            }
        }

        /* Throw an IllegalArgumentException if problems have been detected */
        if (exception.hasEntries()) {
            throw new IllegalArgumentException(exception);
        }

        /* Try to load the requested scripts, and all of it's dependencies. */
        this.recursiveLoader(unqualified, new Stack<String>(), exception,
                depLevels);

        /* Throw the ScriptLoaderException, if it now has entries. */
        if (exception.hasEntries()) {
            throw exception;
        }
    }

    /* Used only by the constructor */
    private void recursiveLoader(Set<String> unqualified, Stack<String> stack,
            ScriptLoaderException exception, List<String> depLevels) {
        for (String unq : unqualified) {

            /*
             * NOTE: There is no need here to check if 'unq' is really valid
             * and unqualified as this check has been done in the constructor
             * of both this class and the Script class.
             */

            String qlf = null;

            try {

                /* Try to find a registered qualified name first */
                qlf = super.getRegisteredName(unq);

            } catch (IllegalArgumentException e) {

                /* Otherwise, search for scripts */
                Set<String> exs = ScriptLoader.getExistingNames(unq,
                        super.getSearch());

                /* Exactly one script should be found */
                if (exs.size() == 0) {
                    exception.getNotFoundNames().add(unq);
                    continue;
                } else if (exs.size() > 1) {
                    exception.getMultipleImplementations().put(unq, exs);
                    continue;
                }

                /* Register the name */
                qlf = exs.toArray(new String[0])[0];
                super.registerName(unq, qlf);
            }

            /* Check for circular dependencies */
            if (stack.contains(qlf)) {
                List<String> subStack = null;

                // Preferred implementation:
                //subStack = stack.subList(stack.indexOf(qlf), stack.size());
                // Alternative implementation:
                subStack = new ArrayList<String>();
                boolean adding = false;
                for (String entry : stack) {
                    if (!adding && entry.equals(qlf)) {
                        adding = true;
                    }

                    if (adding) {
                        subStack.add(entry);
                    }
                }
                // End of alternative implementation

                String[] saveStack = subStack.toArray(new String[0]);
                exception.getCircularDependencies().add(saveStack);
                continue;
            }

            /* Load in the script, if it has not been loaded before */
            Script script = null;

            try {
                script = super.getScript(unq);
            } catch (IllegalArgumentException e) {

                try {
                    script = new Script(qlf, super.getSearch(), depLevels);
                } catch (ScriptNotFoundException snfe) {
                    exception.getNotFoundNames().add(qlf);
                    continue;
                } catch (ScriptParserException spe) {
                    exception.getParserExceptions().add(spe);
                    continue;
                }

                super.registerScript(unq, script);

                /* Process the dependencies */
                stack.push(qlf);
                try {
                    Set<String> depends = script.getDependencies();
                    this.recursiveLoader(depends, stack, exception, depLevels);
                } finally {
                    /* Always pop(), also when exceptions are thrown */
                    stack.pop();
                }

                this.buildOrder.add(qlf);
            }
        }
    }

    /**
     * Returns a <code>List</code> with the qualified names of all
     * <code>Script</code>s constructed through this
     * <code>DynamicScriptLoader</code>.
     * <p>
     * The entries are placed in the order in which the <code>Script</code>s
     * should be executed.
     * <p>
     * If this <code>DynamicScriptLoader</code> was constructed with an empty
     * <code>Set</code>, this method returns an empty <code>List</code>.
     *
     * @see #getBuildOrder(String)
     */
    public List<String> getBuildOrder() {
        List<String> list = new ArrayList<String>();

        list.addAll(this.buildOrder);

        return list;
    }

    /**
     * Returns a <code>List</code> with the qualified names of all
     * <code>Script</code>s constructed through this
     * <code>DynamicScriptLoader</code> which are dependencies of the
     * <code>Script</code> identified by <code>unq</code>.
     * <p>
     * The entries are placed in the order in which the <code>Script</code>s
     * should be executed.
     *
     * @throws IllegalArgumentException if <code>unq</code> does not identify
     *         a <code>Script</code> constructed through this
     *         <code>DynamicScriptLoader</code>.
     *
     * @see #getBuildOrder()
     */
    public List<String> getBuildOrder(String unq) {
        List<String> list = new ArrayList<String>();
        Set<String> deps = super.getScript(unq).getDependencies();

        this.recursiveOrdering(deps, list);                

        list.add(super.getRegisteredName(unq));
        return list;
    }

    /* Used only by getBuildOrder(String) */
    private void recursiveOrdering(Set<String> names, List<String> list) {
        for (String dep : names) {
            Set<String> deps = super.getScript(dep).getDependencies();
            this.recursiveOrdering(deps, list);

            String qName = super.getRegisteredName(dep);
            if(!list.contains(qName)) {
                list.add(qName);
            }
        }
    }

    public List<String> getRevisedBuildOrder(Set<String> namesToRemove) {

        final Set<String> toRemove = this.getDependentOn(namesToRemove);

        /* Now create a new list, without any name from 'toRemove' */
        List<String> ret = new ArrayList<String>();
        for (String name : this.buildOrder) {
            if (!toRemove.contains(Script.getUnqualifiedName(name))) {
                ret.add(name);
            }
        }

        return ret;
    }

    public Set<String> getDependentOn(final Set<String> namesToRemove) {

        /*
         * Create a local copy of 'namesToRemove' so that this local copy
         * can safely be modified.
         */
        Set<String> toRemove = new TreeSet<String>();
        for (String name : namesToRemove) {
            toRemove.add(Script.getUnqualifiedName(name));
            /* throws IllegalArgumentException if name is not valid */
        }

        /*
         * Add any script that depends on any name from 'toRemove' to the
         * 'toRemove' set.
         */
        Set<Script> scripts = super.getScripts();

        int listSize = 0;
        while (listSize != toRemove.size()) {
            listSize = toRemove.size();

            checkScripts:
            for (Script s : scripts) {
                Set<String> deps = s.getDependencies();
                for (String dep : deps) {
                    for (String rm : toRemove) {
                        if (dep.equals(rm)) {
                            toRemove.add(s.getUnqualifiedName());
                            continue checkScripts;
                        }
                    }
                }
            }
        }

        return toRemove;
    }

    /**
     * <strong>Disabled</strong>. An instance of this class is immutable.
     * @throws UnsupportedOperationException always
     */
    @Override
    protected void registerName(String unq, String qlf) {
        throw new UnsupportedOperationException(
                "registerName(String, String)");
    }

    /**
     * <strong>Disabled</strong>. An instance of this class is immutable.
     * @throws UnsupportedOperationException always
     */
    @Override
    protected void registerScript(String unq, Script script) {
        throw new UnsupportedOperationException(
                "registerScript(String, Script)");
    }
}

