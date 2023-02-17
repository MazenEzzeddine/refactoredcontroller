import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class Scalepp {


    static ArrayList<Partition> topicpartitions1 = new ArrayList<>();
    static Instant lastUpScaleDecision = Instant.now();
    static Instant lastDownScaleDecision = Instant.now();
    static int size = 2;
    static double dynamicAverageMaxConsumptionRate = 0.0;
    static double wsla = 5.0;
    static List<Consumer> assignment = new ArrayList<>();
    private static final Logger log = LogManager.getLogger(Scalepp.class);



    public static void scaleAsPerBinPack(int currentsize) {
        log.info("Currently we have this number of consumers group1 {}", currentsize);
        int neededsize = binPackAndScale();
        log.info("We currently need the following consumers for group1 (as per the bin pack) {}", neededsize);
        int replicasForscale = neededsize - currentsize;
        if (replicasForscale > 0 ) {
            //TODO IF and Else IF can be in the same logic
            log.info("We have to upscale  group1 by {}", replicasForscale);
            size= neededsize;
            try (final KubernetesClient k8s = new DefaultKubernetesClient()) {
                k8s.apps().deployments().inNamespace("default").withName("cons1persec").scale(neededsize);
                log.info("I have Upscaled group1 you should have {}", neededsize);
                lastUpScaleDecision = Instant.now();
            }
        }
        else {
            int neededsized = binPackAndScaled();
            int replicasForscaled =  currentsize - neededsized;
            if(replicasForscaled>0) {
                log.info("We have to downscale  group1 by {}", replicasForscaled);
                size= neededsized;
                try (final KubernetesClient k8s = new DefaultKubernetesClient()) {
                    k8s.apps().deployments().inNamespace("default").withName("cons1persec").scale(neededsized);
                    log.info("I have downscaled group1 you should have {}", neededsized);
                }
                lastDownScaleDecision = Instant.now();
                lastUpScaleDecision = Instant.now();
            }
        }
    }



    private static int binPackAndScale() {
        log.info("Inside binPackAndScale ");
        List<Consumer> consumers = new ArrayList<>();
        int consumerCount = 1;
        List<Partition> parts = new ArrayList<>(Scalep.topicpartitions1);
        dynamicAverageMaxConsumptionRate = 175;

        long maxLagCapacity;
        maxLagCapacity = (long) (dynamicAverageMaxConsumptionRate * wsla);
        consumers.add(new Consumer((String.valueOf(consumerCount)), maxLagCapacity, dynamicAverageMaxConsumptionRate));


        for (Partition partition : parts) {
            if (partition.getLag() > maxLagCapacity) {
                log.info("Since partition {} has lag {} higher than consumer capacity times wsla {}" +
                        " we are truncating its lag", partition.getId(), partition.getLag(), maxLagCapacity);
                partition.setLag(maxLagCapacity);
            }
        }
        //if a certain partition has an arrival rate  higher than R  set its arrival rate  to R
        //that should not happen in a well partionned topic
        for (Partition partition : parts) {
            if (partition.getArrivalRate() > dynamicAverageMaxConsumptionRate) {
                log.info("Since partition {} has arrival rate {} higher than consumer service rate {}" +
                                " we are truncating its arrival rate", partition.getId(),
                        String.format("%.2f",  partition.getArrivalRate()),
                        String.format("%.2f", dynamicAverageMaxConsumptionRate));
                partition.setArrivalRate(dynamicAverageMaxConsumptionRate);
            }
        }
        //start the bin pack FFD with sort
        Collections.sort(parts, Collections.reverseOrder());

        Consumer consumer;


        for (Partition partition : parts) {
            int i;
            for ( i = 0; i < consumerCount; i++) {
                //TODO externalize these choices on the inout to the FFD bin pack
                // TODO  hey stupid use instatenous lag instead of average lag.
                // TODO average lag is a decision on past values especially for long DI.

                if ( consumers.get(i).getRemainingLagCapacity() >=  partition.getLag()  &&
                        consumers.get(i).getRemainingArrivalCapacity() >= partition.getArrivalRate()) {
                    consumers.get(i).assignPartition(partition);
                    // we are done with this partition, go to next
                    log.info("i {}", i);
                    log.info("consumerCount {}", consumerCount);

                    break;
                }
                //we have iterated over all the consumers hoping to fit that partition, but nope
                //we shall create a new consumer i.e., scale up

                }
            if (i==consumerCount ) {
                consumerCount++;
                consumer = new Consumer((String.valueOf(consumerCount)), (long) (dynamicAverageMaxConsumptionRate * wsla),
                        dynamicAverageMaxConsumptionRate);
                consumers.add(consumer);
                consumers.get(i).assignPartition(partition);

            }

        }
        log.info(consumers);
        log.info(" The BP up scaler recommended {}", consumers.size());
        return consumers.size();
    }





    private static int binPackAndScaled() {
        log.info("Inside binPackAndScaled ");
        List<Consumer> consumers = new ArrayList<>();
        int consumerCount = 1;
        List<Partition> parts = new ArrayList<>(Scalep.topicpartitions1);
        dynamicAverageMaxConsumptionRate = 175*0.6;

        long maxLagCapacity;
        maxLagCapacity = (long) (dynamicAverageMaxConsumptionRate * wsla);
        consumers.add(new Consumer((String.valueOf(consumerCount)), maxLagCapacity, dynamicAverageMaxConsumptionRate));

        //if a certain partition has a lag higher than R Wmax set its lag to R*Wmax
        // atention to the window
        for (Partition partition : parts) {
            if (partition.getLag() > maxLagCapacity) {
                log.info("Since partition {} has lag {} higher than consumer capacity times wsla {}" +
                        " we are truncating its lag", partition.getId(), partition.getLag(), maxLagCapacity);
                partition.setLag(maxLagCapacity);
            }
        }
        //if a certain partition has an arrival rate  higher than R  set its arrival rate  to R
        //that should not happen in a well partionned topic
        for (Partition partition : parts) {
            if (partition.getArrivalRate() > dynamicAverageMaxConsumptionRate) {
                log.info("Since partition {} has arrival rate {} higher than consumer service rate {}" +
                                " we are truncating its arrival rate", partition.getId(),
                        String.format("%.2f",  partition.getArrivalRate()),
                        String.format("%.2f", dynamicAverageMaxConsumptionRate));
                partition.setArrivalRate(dynamicAverageMaxConsumptionRate);
            }
        }
        //start the bin pack FFD with sort
        Collections.sort(parts, Collections.reverseOrder());
        Consumer consumer;
        for (Partition partition : parts) {
            int i;
            for ( i = 0; i < consumerCount; i++) {
                //TODO externalize these choices on the inout to the FFD bin pack
                // TODO  hey stupid use instatenous lag instead of average lag.
                // TODO average lag is a decision on past values especially for long DI.
                //log.info("hi");
                log.info("i {}", i );

                if ( consumers.get(i).getRemainingLagCapacity() >=  partition.getLag()  &&
                        consumers.get(i).getRemainingArrivalCapacity() >= partition.getArrivalRate()) {
                    consumers.get(i).assignPartition(partition);
                    // we are done with this partition, go to next
                    log.info("i {}", i);
                    log.info("consumerCount {}", consumerCount);
                    break;
                }
                //we have iterated over all the consumers hoping to fit that partition, but nope
                //we shall create a new consumer i.e., scale up


            }
            if(i == consumerCount) {
                consumerCount++;
                consumer = new Consumer((String.valueOf(consumerCount)), maxLagCapacity,
                        dynamicAverageMaxConsumptionRate);
                consumer.assignPartition(partition);
                consumers.add(consumer);
            }
        }

        log.info(" The BP down scaler recommended {}", consumers.size());
        log.info("===================================");
        return consumers.size();
    }






}
