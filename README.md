# LogPulse

This project is a command line utility that can be used to monitor a file, '/tmp/access.log' by default, and report general statistics and high traffic events periodically.
Every 10 seconds, general statistics are reported (stdout) for all data appended to the file during those 10 seconds. 
Every 120 seconds the associated data is analysed: 

  - whenever the total traffic exceeds a certain threshold, on average, a message 'High traffic' is reported. 
  - whenever the total traffic drops again below the threshold, on average, another message 'Back to normal traffic' is reported. 
  - both messages give details about the time when the threshold was crossed. The default value for the  threshold is 10.0 requests per second. 
                    
The contents of the file are expected to be lines in CLF format:
https://publib.boulder.ibm.com/tividd/td/ITWSA/ITWSA_info45/en_US/HTML/guide/c-logs.html#common
                    
To change defaults, please provide a list of arguments containing the keys to override, each followed by the value. For example:

    ./logpulse -gsp 5 -f logpulse-store/testLogs.log                         
    
    will work on file 'logpulse-store/testLogs.log' and will report 
    general statistics every 5 seconds, instead of 10.

Available parameter keys:

    -h | -help: Shows this text. This key takes not value.
                    
    -f | -file | -in: Path (absolute, or relative to the launch script's location) 
            of the file being processed. 
            Value type: text, default: /tmp/access.log
                            
    -gsp | -generalStatsPeriod: Sets the period (seconds)  for general statistics 
            reporting. 
            Value type: int, default: 10
                             
    -tgp | -trafficGaugePeriod: Sets the period (seconds) for high traffic gauge's 
            reporting. 
            Value type: int, default: 120
                            
    -tgt | -trafficGaugeThreshold: Sets the threshold (requests per second on avg. 
            for the considered period) for high traffic reporting. 
            Value type: double, default: 10.0 

# For development

_Requirements_: **JDK15**, **Gradle (preferably 6.x.x, we use 6.7)** are required.

To create the documentation: ./gradlew javadoc

To build the project:        ./gradlew clean build

The main artefact resulting from the build can be found under: **build/libs/logpulse-1.0-SNAPSHOT-all.jar**,
an uberjar containing all dependencies so that you may run the application with a command like:

    java -Xmx1G -Dfile.encoding=UTF-8 -Dlog4j.debug=false -jar build/libs/logpulse-1.0-SNAPSHOT-all.jar -help
    
However, use the more convenient launch command: 

    ./logpulse
    
Note: *-Dlog4j.debug=true* will result in DEBUG level logs to be reported.




            
