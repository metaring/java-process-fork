/**
 *    Copyright 2019 MetaRing s.r.l.
 * 
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 * 
 *        http://www.apache.org/licenses/LICENSE-2.0
 * 
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.metaring.java.process.fork;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.BiConsumer;

public class JavaChildProcess {

    public static final boolean IS_WINDOWS = !File.pathSeparator.equals(":");
    public static final String QUOTES = IS_WINDOWS ? "\"" : "";

    static final String CLOSE_KEY = JavaChildProcess.class.getName() + ".KILL";
    static final String CONMMAND_SEPARATOR = "[ - SEPARATOR - ][" + JavaChildProcess.class.getName() + "][ - SEPARATOR - ]";

    public static void main(String[] args) throws Exception {
        String[] commands = null;
        String folder = null;
        File[] tempFilesToDelete;
        try (Scanner scanner = new Scanner(JavaChildProcess.class.getClassLoader().getResourceAsStream("context.txt"))) {
            folder = scanner.nextLine();
            String line;
            List<String> l = new ArrayList<>();
            while (!(line = scanner.nextLine()).equalsIgnoreCase(CONMMAND_SEPARATOR)) {
                l.add(line);
            }
            commands = l.toArray(new String[l.size()]);
            List<File> files = new ArrayList<>();
            while (scanner.hasNext()) {
                files.add(new File(scanner.nextLine()));
            }
            tempFilesToDelete = files.toArray(new File[files.size()]);
        }
        final String finalFolder = folder;
        String javaCommand = QUOTES + System.getProperty("java.home").replace("\\", "/");
        if (!javaCommand.endsWith("/")) {
            javaCommand += "/";
        }
        javaCommand += "bin/java" + QUOTES;

        final List<String> finalJavaCommands = new ArrayList<>();
        finalJavaCommands.add(javaCommand);
        finalJavaCommands.add("-cp");
        finalJavaCommands.add(QUOTES + folder + QUOTES);
        finalJavaCommands.add(JavaChildProcess.class.getName());
        if (args != null && args.length > 0) {
            boolean alsoFolder = true;
            boolean isByHandle = Boolean.parseBoolean(args[1]);
            long pid = -1;
            if (args[0].equalsIgnoreCase("c")) {
                pid = Long.parseLong(args[2]);
            }
            else {
                try (Scanner scanner = new Scanner(System.in)) {
                    alsoFolder = false;
                    pid = Long.parseLong(scanner.nextLine());
                    scanner.nextLine();
                }
                catch (Exception e) {
                }
            }
            try {
                Thread.sleep(1700);
            }
            catch (Exception e) {
            }
            try {
                exit(null, isByHandle, pid, tempFilesToDelete, finalFolder, finalJavaCommands, alsoFolder);
            }
            catch (Exception e) {
            }
        }
        else {
            List<Boolean> keepRunning = new ArrayList<>();
            keepRunning.add(true);
            final Process finalProcess = Runtime.getRuntime().exec(commands);
            PidRetriever pidRetriever = PidRetriever.getProcessPid(finalProcess);
            System.out.println(pidRetriever.isByHandle());
            System.out.println(pidRetriever.getPid());
            BiConsumer<Process, Exception> processConsumer = (p, e) -> {
                if (e != null) {
                    e.printStackTrace();
                }
                if (p != null && !p.isAlive()) {
                    if (!keepRunning.isEmpty()) {
                        System.err.println("Process not alive anymore. Exiting and Cleaning temp files...");
                        try {
                            exit(finalProcess, pidRetriever.isByHandle(), pidRetriever.getPid(), tempFilesToDelete, finalFolder, finalJavaCommands, false);
                        }
                        catch (Exception e1) {
                        }
                        System.exit(200);
                    }
                }
            };
            new ProcessWaiter(finalProcess, processConsumer);
            new ProcessStreamReader(new BufferedReader(new InputStreamReader(finalProcess.getErrorStream())), System.err, processConsumer);
            new ProcessStreamReader(new BufferedReader(new InputStreamReader(finalProcess.getInputStream())), System.out, processConsumer);
            // List<String> gcCommandList = new ArrayList<>();
            // gcCommandList.addAll(finalJavaCommands);
            // gcCommandList.add("gc");
            // gcCommandList.add("" + pidRetriever.isByHandle());
            // Process gc = Runtime.getRuntime().exec(gcCommandList.toArray(new String[gcCommandList.size()]));
            // gc.getOutputStream().write(("" + pidRetriever.getPid()).getBytes());
            // gc.getOutputStream().flush();
            // gc.getOutputStream().write("\n".getBytes());
            // gc.getOutputStream().flush();
            // new ProcessWaiter(gc, processConsumer);
            PrintWriter printWriter = new PrintWriter(finalProcess.getOutputStream());
            try (Scanner scanner = new Scanner(System.in)) {
                while (!keepRunning.isEmpty()) {
                    String line = scanner.nextLine();
                    if (line == null || line.equalsIgnoreCase(CLOSE_KEY)) {
                        keepRunning.remove(0);
                        break;
                    }
                    else {
                        printWriter.println(line);
                        printWriter.flush();
                    }
                }
            }
            catch (Exception e) {
                keepRunning.remove(0);
            }
            try {
                exit(finalProcess, pidRetriever.isByHandle(), pidRetriever.getPid(), tempFilesToDelete, finalFolder, finalJavaCommands, false);
            }
            catch (Exception e) {
            }
        }
    }

    static final void exit(Process process, boolean isByHandle, long pid, File[] tempFilesToDelete, String folder, List<String> javaCommands, boolean alsoFolder) {
        try {
            process.destroy();
        }
        catch (Exception e) {
        }
        ProcessKiller.kill(pid, isByHandle);
        try {
            Thread.sleep(700);
        }
        catch (Exception e) {
        }
        if (tempFilesToDelete != null && tempFilesToDelete.length > 0) {
            for (File file : tempFilesToDelete) {
                delete(file);
            }
        }
        if (alsoFolder) {
            delete(new File(folder));
        }
        else {
            try {
                List<String> cleanCommands = new ArrayList<>();
                cleanCommands.addAll(javaCommands);
                cleanCommands.add("c");
                cleanCommands.add("" + isByHandle);
                cleanCommands.add("" + pid);
                Runtime.getRuntime().exec(cleanCommands.toArray(new String[cleanCommands.size()]));
            }
            catch (Exception e) {
            }
        }
    }

    private static final void delete(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory()) {
                        delete(f);
                    }
                    else {
                        f.delete();
                        f.deleteOnExit();
                    }
                }
            }
        }
        file.delete();
        file.deleteOnExit();
    }
}
