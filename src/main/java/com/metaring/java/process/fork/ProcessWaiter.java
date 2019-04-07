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

import java.util.function.BiConsumer;

class ProcessWaiter extends Thread {

    private Process process;
    private BiConsumer<Process, Exception> consumer;

    ProcessWaiter(Process process, BiConsumer<Process, Exception> consumer) {
        this.process = process;
        this.consumer = consumer;
        this.start();
    }

    @Override
    public void run() {
        try {
            process.waitFor();
        }
        catch (Exception e) {
            if(consumer != null) {
                consumer.accept(process, e);
            }
        }
        if(consumer != null) {
            consumer.accept(process, null);
        }
    }
}
