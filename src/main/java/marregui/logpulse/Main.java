/* **
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Copyright 2020, Miguel Arregui a.k.a. marregui
 */
package marregui.logpulse;

import marregui.logpulse.clf.CLF;
import marregui.logpulse.clf.CLFReadoutHandler;
import marregui.logpulse.clf.stats.GeneralStats;
import marregui.logpulse.clf.stats.GeneralStatsView;
import marregui.logpulse.clf.stats.HighTrafficGauge;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * LogPulse's main method, application's entry point.
 */
public class Main {

    private static class Parameters {
        int generalStatsPeriod = GeneralStats.DEFAULT_PERIOD_SECS;
        int trafficGaugePeriod = HighTrafficGauge.DEFAULT_PERIOD_SECS;
        double trafficGaugeThreshold = HighTrafficGauge.DEFAULT_REQUESTS_PER_SECOND_THRESHOLD;
        Path file = Paths.get("/tmp/access.log");
        static Parameters parseArgs(String[] args) {
            if (args.length == 1 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
                usage();
                System.exit(0);
            }
            if (args.length % 2 != 0) {
                System.err.println("Odd number of parameters");
                usage();
                System.exit(1);
            }

            Parameters parameters = new Parameters();
            for (int i = 0; i < args.length; i += 2) {
                String key = args[i];
                String val = args[i + 1];
                try {
                    switch (key) {
                        case "-f", "-file", "-in" ->
                                parameters.file = Paths.get(val);
                        case "-gsp", "-generalStatsPeriod" ->
                                parameters.generalStatsPeriod = Integer.parseInt(val);
                        case "-tgp", "-trafficGaugePeriod" ->
                                parameters.trafficGaugePeriod = Integer.parseInt(val);
                        case "-tgt", "-trafficGaugeThreshold" ->
                                parameters.trafficGaugeThreshold = Double.parseDouble(val);
                        case "-h", "-help" -> {
                            System.err.println("Help command should stand on its own");
                            usage();
                            System.exit(1);
                        }
                        default -> {
                            System.err.printf("Unrecognized parameter key [%s, %s]", key, val);
                            usage();
                            System.exit(1);
                        }
                    }
                } catch (NumberFormatException e) {
                    System.err.printf("Unrecognized number format for parameter value [%s, %s]", key, val);
                }
            }
            return parameters;
        }

        static void usage() {
            System.out.println("""
                    
                    LogPulse
                    ~~~~~~~~
                    LogPulse is a command line utility that can be used to monitor a file, '/tmp/access.log' by default, 
                    and report general statistics and high traffic events periodically.
                    Every 10 seconds, general statistics are reported (stdout) for all data appended to the file during 
                    those 10 seconds.
                    Every 120 seconds the associated data is analysed:
                      - whenever the total traffic exceeds a certain threshold, on average, a message 'High traffic' is 
                        reported.
                      - whenever the total traffic drops again below the threshold, on average, another message 'Back to
                        normal traffic' is reported.
                      - both messages give details about the time when the threshold was crossed. The default value for
                        the  threshold is 10.0 requests per second.
                                                                                
                    The contents of the file are expected to be lines in CLF format:
                        https://publib.boulder.ibm.com/tividd/td/ITWSA/ITWSA_info45/en_US/HTML/guide/c-logs.html#common
                                                                                
                    To change defaults, please provide a list of arguments containing the keys to override, each followed
                    by the value. For example:
                                                             
                        ./logpulse -gsp 5 -f logpulse-store/testLogs.log
                                                                
                        will work on file 'logpulse-store/testLogs.log' and will report
                        general statistics every 5 seconds, instead of 10.
                                                             
                    Available parameter keys:
                                                             
                        -h | -help: Shows this text. This key takes not value.
                                                                                
                        -f | -file | -in: Path (absolute, or relative to the launch script's location) of the file being 
                              processed.
                              Value type: text, default: /tmp/access.log
                                                                                        
                        -gsp | -generalStatsPeriod: Sets the period (seconds)  for general statistics reporting.
                              Value type: int, default: 10
                                                                                         
                        -tgp | -trafficGaugePeriod: Sets the period (seconds) for high traffic gauge's reporting.
                              Value type: int, default: 120
                                                                                        
                        -tgt | -trafficGaugeThreshold: Sets the threshold (requests per second on avg. for the considered 
                              period) for high traffic reporting.
                              Value type: double, default: 10.0
                    """);
        }
    }

    /**
     * LogPulse application's main method.
     *
     * @param args described by passing a single arg equals to (-h, -help)
     */
    public static void main(String[] args) {
        Parameters parameters = Parameters.parseArgs(args);
        Scheduler<CLF> scheduler = new Scheduler<>(new CLFReadoutHandler(parameters.file), false);
        scheduler.setPeriodicSchedule(new GeneralStatsView(System.out));
        scheduler.setPeriodicSchedule(new HighTrafficGauge(System.out));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                scheduler.stop();
            } catch (IllegalStateException ignore) {
                // it means it is already stopped, likely because
                // the parent folder has been deleted
            }
        }, "logpulse-shutdown-hook"));
        scheduler.start();
    }
}
