/* **
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
 *
 * Copyright 2020, Miguel Arregui a.k.a. marregui
 */
package marregui.logpulse.clf;

import marregui.logpulse.FileReadoutHandler;

import java.nio.file.Path;

/**
 * Instances of this class provide parsing for CLF.
 *
 * @see FileReadoutHandler
 * @see CLF
 * @see CLFParser
 */
public class CLFReadoutHandler extends FileReadoutHandler<CLF> {

    /**
     * Constructor
     * @param file file to be readout
     */
    public CLFReadoutHandler(Path file) {
        super(file);
    }

    @Override
    public CLF parseLine(String line) {
        return CLFParser.parseLogLine(line);
    }
}
