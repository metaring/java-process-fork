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
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinNT.HANDLE;

public class JavaProcessFork {

    private static final String JNA_JAR_FILE_PATH;
    private static final String JNA_JAR_FILE_NAME;

    private static final String JNA_PLATFORM_JAR_FILE_PATH;
    private static final String JNA_PLATFORM_JAR_FILE_NAME;

    static {
        String jnaPath = Native.class.getClassLoader().getResource(Native.class.getName().replace(".", "/") + ".class").toString();
        jnaPath = jnaPath.substring(0, jnaPath.indexOf("!")).replace("jar:file:", "").replace("\\", "/");
        JNA_JAR_FILE_PATH = jnaPath;
        JNA_JAR_FILE_NAME = jnaPath.substring(jnaPath.lastIndexOf("/") + 1);
        String jnaPlatformPath = HANDLE.class.getClassLoader().getResource(HANDLE.class.getName().replace(".", "/") + ".class").toString();
        jnaPlatformPath = jnaPlatformPath.substring(0, jnaPlatformPath.indexOf("!")).replace("jar:file:", "").replace("\\", "/");
        JNA_PLATFORM_JAR_FILE_PATH = jnaPlatformPath;
        JNA_PLATFORM_JAR_FILE_NAME = jnaPlatformPath.substring(jnaPlatformPath.lastIndexOf("/") + 1);
    }

    public static final JavaProcessFork fork(String command, File... tempFilesToDelete) throws Exception {
        return fork(new String[] { command }, true, true, tempFilesToDelete);
    }

    public static final JavaProcessFork forkNoRedirect(String command, File... tempFilesToDelete) throws Exception {
        return fork(new String[] { command }, false, false, tempFilesToDelete);
    }

    public static final JavaProcessFork fork(String command, boolean redirectInputStream, boolean redirectErrorStream, File... tempFilesToDelete) throws Exception {
        return fork(new String[] { command }, redirectInputStream, redirectErrorStream, tempFilesToDelete);
    }

    public static final JavaProcessFork fork(String[] commands, File... tempFilesToDelete) throws Exception {
        return fork(commands, true, true, tempFilesToDelete);
    }

    public static final JavaProcessFork forkNoRedirect(String[] commands, File... tempFilesToDelete) throws Exception {
        return fork(commands, false, false, tempFilesToDelete);
    }

    public static final JavaProcessFork fork(String[] commands, boolean redirectInputStream, boolean redirectErrorStream, File... tempFilesToDelete) throws Exception {

        Path rootPath = Files.createTempDirectory(JavaProcessFork.class.getSimpleName());
        File root = rootPath.toFile();
        String rootString = root.getAbsolutePath().replace("\\", "/");
        if (!rootString.endsWith("/")) {
            rootString += "/";
        }

        final String finalRootString = rootString;
        Path nameSpace = Files.createDirectories(rootPath.resolve(JavaProcessFork.class.getPackage().getName().replace(".", "/")));
        copyClass(nameSpace, JavaChildProcess.class);
        copyClass(nameSpace, PidRetriever.class);
        copyClass(nameSpace, ProcessStreamReader.class);
        copyClass(nameSpace, ProcessWaiter.class);
        copyClass(nameSpace, ProcessKiller.class);
        if (JavaChildProcess.IS_WINDOWS) {
            Files.copy(new FileInputStream(JNA_JAR_FILE_PATH), rootPath.resolve(JNA_JAR_FILE_NAME));
            Files.copy(new FileInputStream(JNA_PLATFORM_JAR_FILE_PATH), rootPath.resolve(JNA_PLATFORM_JAR_FILE_NAME));
        }
        StringBuilder sb = new StringBuilder(rootString);
        for (String command : commands) {
            sb.append("\n").append(command);
        }
        sb.append("\n").append(JavaChildProcess.CONMMAND_SEPARATOR);
        if (tempFilesToDelete != null && tempFilesToDelete.length > 0) {
            for (File tempFile : tempFilesToDelete) {
                sb.append("\n").append(tempFile.getAbsolutePath().replace("\\", "/"));
            }
        }
        Files.write(rootPath.resolve("context.txt"), sb.toString().getBytes());
        String javaCommand = JavaChildProcess.QUOTES + System.getProperty("java.home").replace("\\", "/");
        if (!javaCommand.endsWith("/")) {
            javaCommand += "/";
        }
        javaCommand += "bin/java" + JavaChildProcess.QUOTES;

        final List<String> javaCommands = new ArrayList<>();
        javaCommands.add(javaCommand);
        javaCommands.add("-cp");
        javaCommands.add(JavaChildProcess.QUOTES + rootString + (JavaChildProcess.IS_WINDOWS ? (File.pathSeparator + rootString + "*") : "") + JavaChildProcess.QUOTES);
        javaCommands.add(JavaChildProcess.class.getName());
        final Process process = Runtime.getRuntime().exec(javaCommands.toArray(new String[javaCommands.size()]));
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    process.getOutputStream().write(JavaChildProcess.CLOSE_KEY.getBytes());
                    process.getOutputStream().flush();
                    process.getOutputStream().write("\n".getBytes());
                    process.getOutputStream().flush();
                    process.getOutputStream().close();
                    process.waitFor();
                }
                catch (Exception e) {
                }
            }
        });

        final JavaProcessFork javaProcessFork = new JavaProcessFork();
        javaProcessFork.jVMProcessPid = PidRetriever.getMyPid();
        javaProcessFork.childJVMProcessTempFolder = root;
        javaProcessFork.childJVMProcessCommand = javaCommand;
        javaProcessFork.childJVMProcessInputStream = process.getInputStream();
        javaProcessFork.childJVMProcessErrorStream = process.getErrorStream();
        javaProcessFork.childJVMProcessOutputStream = process.getOutputStream();
        javaProcessFork.inputStreamRedirected = redirectInputStream;
        javaProcessFork.errorStreamRedirected = redirectErrorStream;
        javaProcessFork.childJVMProcessPid = PidRetriever.getProcessPid(process).getPid();
        javaProcessFork.childProcessCommands = commands;
        javaProcessFork.childProcessTempFiles = tempFilesToDelete;
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(javaProcessFork.childJVMProcessInputStream));
        javaProcessFork.childProcessPidObtainedByHandle = Boolean.parseBoolean(bufferedReader.readLine());
        javaProcessFork.childProcessPid = Long.parseLong(bufferedReader.readLine());
        new ProcessWaiter(process, (p, e) -> JavaChildProcess.exit(p, javaProcessFork.childProcessPidObtainedByHandle, javaProcessFork.childProcessPid, tempFilesToDelete, finalRootString, javaCommands, true));
        if (redirectInputStream) {
            new ProcessStreamReader(bufferedReader, System.out);
        }
        if (redirectErrorStream) {
            new ProcessStreamReader(new BufferedReader(new InputStreamReader(javaProcessFork.childJVMProcessErrorStream)), System.err);
        }

        return javaProcessFork;
    }

    private static final void copyClass(Path nameSpace, Class<?> clazz) throws Exception {
        Files.copy(JavaProcessFork.class.getClassLoader().getResourceAsStream(clazz.getName().replace(".", "/") + ".class"), nameSpace.resolve(clazz.getSimpleName() + ".class"));
    }

    private long jVMProcessPid;
    private long childJVMProcessPid;
    private InputStream childJVMProcessInputStream;
    private InputStream childJVMProcessErrorStream;
    private OutputStream childJVMProcessOutputStream;
    private boolean inputStreamRedirected;
    private boolean errorStreamRedirected;
    private String childJVMProcessCommand;
    private File childJVMProcessTempFolder;
    private long childProcessPid;
    private boolean childProcessPidObtainedByHandle;
    private String[] childProcessCommands;
    private File[] childProcessTempFiles;

    private JavaProcessFork() {
    }

    public long getjVMProcessPid() {
        return jVMProcessPid;
    }

    public long getChildJVMProcessPid() {
        return childJVMProcessPid;
    }

    public InputStream getChildJVMProcessInputStream() {
        return childJVMProcessInputStream;
    }

    public InputStream getChildJVMProcessErrorStream() {
        return childJVMProcessErrorStream;
    }

    public OutputStream getChildJVMProcessOutputStream() {
        return childJVMProcessOutputStream;
    }

    public boolean isInputStreamRedirected() {
        return inputStreamRedirected;
    }

    public boolean isErrorStreamRedirected() {
        return errorStreamRedirected;
    }

    public String getChildJVMProcessCommand() {
        return childJVMProcessCommand;
    }

    public File getChildJVMProcessTempFolder() {
        return childJVMProcessTempFolder;
    }

    public boolean isChildProcessPidObtainedByHandle() {
        return childProcessPidObtainedByHandle;
    }

    public long getChildProcessPid() {
        return childProcessPid;
    }

    public String[] getChildProcessCommands() {
        return childProcessCommands;
    }

    public File[] getChildProcessTempFiles() {
        return childProcessTempFiles;
    }

    public void terminateProcess() {
        try {
            childJVMProcessOutputStream.write(JavaChildProcess.CLOSE_KEY.getBytes());
            childJVMProcessOutputStream.flush();
            childJVMProcessOutputStream.write("\n".getBytes());
            childJVMProcessOutputStream.flush();
            childJVMProcessOutputStream.close();
        }
        catch (Exception e) {
        }
    }
}
