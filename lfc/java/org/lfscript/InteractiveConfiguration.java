/*
 * - InteractiveConfiguration.java -
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

import org.lfscript.buildmgr.XStartConfigurator;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;

import java.io.InputStreamReader;
import java.io.BufferedReader;

/**
 * @author Marcel van den Boer
 * @version 2012-01-24
 */
class InteractiveConfiguration {
    public static void main(String... args) {
        if (!XStartConfigurator.getWindowManagers().isEmpty()) {
            InteractiveConfiguration.configureXinitrc();
            InteractiveConfiguration.configureInittab();
        }
    }

    private static void configureXinitrc() {
        final List<String> options = new ArrayList<String>();
        options.addAll(XStartConfigurator.getWindowManagers());

        final String selected;

        if (options.size() == 0) {
            return;
        } else if (options.size() == 1) {
            selected = options.toArray(new String[0])[0];

            System.out.println("You have one Window Manager installed; '"
                    + selected + "' will be loaded when you issue\n"
                    + "'startx' on the command line.\n");
        } else {
            System.out.println("You have multiple Window Managers (desktop "
                    + "environments) installed.\nWhich one would you like to "
                    + "load after issuing 'startx' on the command line?\n\n"
                    + "You can override this later on a per user basis by "
                    + "creating a file called\n'.xinitrc' in the home "
                    + "directory.\n");

            selected = InteractiveConfiguration.selectOption(options);
        }

        try {
            XStartConfigurator.setWindowManager(selected);
        } catch (IOException e) {
            System.out.println("\nError while configuring Window Manager:");
            e.printStackTrace();
            System.exit(1);
        }

    }

    private static void configureInittab() {
        System.out.print("Would you like to make the system boot to runlevel 5 "
                + "(graphical mode) by\ndefault? This is only effective if you "
                + "installed a Display Manager (a graphical\nlogin screen) or "
                + "a bootscript which launches X in this runlevel.\n\nType 'y' "
                + "or 'yes' to boot to graphical mode: ");

        final String input = InteractiveConfiguration.readLine().toLowerCase();

        try {
            XStartConfigurator.setGraphicalBoot(
                    input.equals("yes") || input.equals("y"));
        } catch (IOException e) {
            System.out.println("\nError while updating /etc/inittab:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String selectOption(final List<String> options) {
        int i = 1;
        for (final String option : options) {
            System.out.println(i + ". " + option);
            i++;
        }

        System.out.println("");
        int selection = -1;

        while (selection == -1) {
            System.out.print("Type in the number corresponding with your "
                    + "selection and press ENTER: ");

            final String input = InteractiveConfiguration.readLine();

            try {
                selection = Integer.parseInt(input);

                if (1 > selection || selection > options.size()) {
                    System.out.println("Selection not in range.");
                    selection = -1;
                }

            } catch (NumberFormatException e) {
                System.out.println("That is not a valid number.");
            }
        }

        System.out.print("\n");

        return options.get(selection - 1);
    }

    private static String readLine() {
        String in = null;
        try {
            BufferedReader read = new BufferedReader(new InputStreamReader(
                    System.in));
            in = read.readLine();
        } catch (IOException e) {
            System.out.println("ERROR: Console input");
            e.printStackTrace();
            System.exit(1);
        }
        return in;
    }
}

