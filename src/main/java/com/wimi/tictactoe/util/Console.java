/*
 * Copyright 2019 Akshit Sinha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wimi.tictactoe.util;

import java.time.Instant;

/**
 * @Description Logging messages to console output.
 */
public class Console {

    /**
     * Logs a message to the Console.
     *
     * @param log Message.
     */
    public static void log(Object log) {
        System.out.println("[" + Instant.now() + "] " + log);
    }
}