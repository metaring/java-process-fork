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

import java.lang.reflect.Field;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT.HANDLE;

public class PidRetriever {

    @SuppressWarnings("restriction")
    public static final long getMyPid() throws Exception {
        // todo check is this the equivalent of what have before https://dzone.com/articles/java-9-besides-modules
        return ProcessHandle.current().pid();
    }

    public static final PidRetriever getProcessPid(Process process) {
        PidRetriever pidRetriever = new PidRetriever();
        try {
            Class<?> processImplClass = process.getClass();
            Field processPidField = null;
            try {
                processPidField = processImplClass.getDeclaredField("pid");
            }
            catch (Exception e) {
                processPidField = processImplClass.getDeclaredField("handle");
                pidRetriever.byHandle = true;
            }
            processPidField.setAccessible(true);
            pidRetriever.pid = Long.parseLong(processPidField.get(process).toString());
            if (pidRetriever.byHandle) {
                pidRetriever.pid = getPidByHandle(pidRetriever.pid);
            }
        }
        catch (Exception e) {
        }
        return pidRetriever;
    }

    private static final long getPidByHandle(long handleId) {
        try {
            Kernel32 kernel = Kernel32.INSTANCE;
            HANDLE handle = new HANDLE();
            Pointer pointer = Pointer.createConstant(handleId);
            handle.setPointer(pointer);
            long pid = kernel.GetProcessId(handle);
            if (pid == 0) {
                pid = handleId;
            }
            return pid;
        }
        catch (Exception e) {
            e.printStackTrace();
            return handleId;
        }
    }

    private boolean byHandle = false;
    private long pid;

    private PidRetriever() {
    }

    public boolean isByHandle() {
        return byHandle;
    }

    public long getPid() {
        return pid;
    }
}
