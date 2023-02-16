import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

public class Main {

    private static final Logger log = LogManager.getLogger(Main.class);


    public static void main(String[] args) throws InterruptedException, ExecutionException {
        for (int i = 0; i <= 4; i++) {
            ArrivalRates.topicpartitions4.add(new Partition(i, 0, 0));
            ArrivalRates.topicpartitions3.add(new Partition(i, 0, 0));
            Scalep.topicpartitions1.add(new Partition(i, 0, 0));
            Scale2p.topicpartitions2.add(new Partition(i, 0, 0));
            Scale5p.topicpartitions5.add(new Partition(i, 0, 0));
            Scale5p.topicpartitions5avg.add(new Partition(i, 0, 0));
        }
     /*   log.info("Warming for 3 minutes seconds.");
        Thread.sleep(180000);*/
        log.info("Warming for 2 minutes seconds.");
        Thread.sleep(60*2*1000);
        while (true) {
            log.info("Querying Prometheus");
            Main.QueryingPrometheus();
            log.info("Sleeping for 5 seconds");
            log.info("========================================");
            Thread.sleep(5000);
        }
    }


    static void QueryingPrometheus() throws ExecutionException, InterruptedException {


        ///////////////////////////////////////////////////////////////////////////////////////////////
        //
        ArrivalRates.arrivalRateTopic1();
       //ArrivalRates.arrivalRateTopic2();
        //ArrivalRates.arrivalRateTopic3();
         //ArrivalRates.arrivalRateTopic4();
        //ArrivalRates.arrivalRateTopic5();
        //arrivalRateTopic5Avg();

        if (Duration.between(Scalep.lastUpScaleDecision, Instant.now()).getSeconds() > 15) {
            //QueryRate.queryConsumerGroup();
            Scalep.scaleAsPerBinPack(Scalep.size);
        }

        if (Duration.between(Scale2p.lastUpScaleDecision, Instant.now()).getSeconds() > 15) {
            //QueryRate.queryConsumerGroup();
            Scale2p.scaleAsPerBinPack(Scale2p.size);
        }

        if (Duration.between(Scale5p.lastUpScaleDecision, Instant.now()).getSeconds() > 15) {
           // QueryRate.queryConsumerGroup();
            Scale5p.scaleAsPerBinPack(Scale5p.size);
        }
    }






}
