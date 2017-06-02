/*
 */
package hostFaultInjection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.cloudbus.cloudsim.brokers.DatacenterBroker;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.util.Log;
import org.cloudsimplus.testbeds.ExperimentRunner;

/**
 * * Runs the {@link HostFaultInjectionExperiment} the number of
 * times defines by {@link #getSimulationRuns()} and compute statistics.
 *
 * @author raysaoliveira
 */
class HostFaultInjectionRunner extends ExperimentRunner<HostFaultInjectionExperiment> {
    /**
     * Different lengths that will be randomly assigned to created Cloudlets.
     */
    static final long[] CLOUDLET_LENGTHS = {1000_000_000L, 1800_000_000L, 2800_000_000L};
    static final int VMS = 3;
    static final int CLOUDLETS = 6;

    /**
     * Datacenter availability for each experiment.
     */
    private List<Double> availability;

    /**
     * The percentage of brokers meeting Availability average for all the
     * experiments.
     */
    private List<Double> percentageOfBrokersMeetingAvailability;

    private List<Double> ratioVmsPerHost;


    /**
     * Indicates if each experiment will output execution logs or not.
     */
    private final boolean experimentVerbose = false;

    /**
     * Starts the execution of the experiments the number of times defines in
     * {@link #getSimulationRuns()}.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new HostFaultInjectionRunner(true, 1475098589732L)
            .setSimulationRuns(300)
            .setNumberOfBatches(5) //Comment this or set to 0 to disable the "Batch Means Method"
            .setVerbose(true)
            .run();
    }

    HostFaultInjectionRunner(final boolean applyAntitheticVariatesTechnique, final long baseSeed) {
        super(applyAntitheticVariatesTechnique, baseSeed);
        availability = new ArrayList<>();
        percentageOfBrokersMeetingAvailability = new ArrayList<>();
        ratioVmsPerHost = new ArrayList<>();
    }

    @Override
    protected HostFaultInjectionExperiment createExperiment(int i) {
        HostFaultInjectionExperiment exp = new HostFaultInjectionExperiment(i, this);
        exp.setVerbose(experimentVerbose)
            .setAfterExperimentFinish(this::afterExperimentFinish);
        return exp;
    }

    /**
     * Method automatically called after every experiment finishes running. It
     * performs some post-processing such as collection of data for statistic
     * analysis.
     *
     * @param exp the finished experiment
     */
    private void afterExperimentFinish(HostFaultInjectionExperiment exp) {
        availability.add(exp.getFaultInjection().availability() * 100);
        percentageOfBrokersMeetingAvailability.add(exp.getPercentageOfAvailabilityThatMeetingTheSla());
        ratioVmsPerHost.add(exp.getRatioVmsPerHost());
    }

    @Override
    protected void setup() {/**/}

    @Override
    protected Map<String, List<Double>> createMetricsMap() {
        Map<String, List<Double>> map = new HashMap<>();
        map.put("Average of Total Availability of Simulation", availability);
        map.put(" Percentagem of brokers meeting the Availability: ", percentageOfBrokersMeetingAvailability);
        map.put("VMs/Hosts Ratio: ", ratioVmsPerHost);
        return map;
    }

    @Override
    protected void printSimulationParameters() {
        System.out.printf("Executing %d experiments. Please wait ... It may take a while.\n", getSimulationRuns());
        System.out.println("Experiments configurations:");
        System.out.printf("\tBase seed: %d | Number of VMs: %d | Number of Cloudlets: %d\n", getBaseSeed(), VMS, CLOUDLETS);
        System.out.printf("\tApply Antithetic Variates Technique: %b\n", isApplyAntitheticVariatesTechnique());
        if (isApplyBatchMeansMethod()) {
            System.out.println("\tApply Batch Means Method to reduce simulation results correlation: true");
            System.out.printf("\tNumber of Batches for Batch Means Method: %d", getNumberOfBatches());
            System.out.printf("\tBatch Size: %d\n", batchSizeCeil());
        }
        System.out.printf("\nSimulated Annealing Parameters\n");
    }

    @Override
    protected void printFinalResults(String metricName, SummaryStatistics stats) {
        System.out.printf("\n# %s for %d simulation runs\n", metricName, getSimulationRuns());
        if (!simulationRunsAndNumberOfBatchesAreCompatible()) {
            System.out.println("\tBatch means method was not be applied because the number of simulation runs is not greater than the number of batches.");
        }
        if (getSimulationRuns() > 1) {
            showConfidenceInterval(stats);
        }
    }

    private void showConfidenceInterval(SummaryStatistics stats) {
        // Calculate 95% confidence interval
        double intervalSize = computeConfidenceErrorMargin(stats, 0.95);
        double lower = stats.getMean() - intervalSize;
        double upper = stats.getMean() + intervalSize;
        System.out.printf(
            "\t This METRIC Rmean 95%% Confidence Interval: %.4f ∓ %.4f, that is [%.4f to %.4f]\n",
            stats.getMean(), intervalSize, lower, upper);
        System.out.printf("\tStandard Deviation: %.4f \n", stats.getStandardDeviation());
    }

}
