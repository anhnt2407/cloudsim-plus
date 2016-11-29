/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.datacenters.power;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.datacenters.DatacenterCharacteristics;
import org.cloudbus.cloudsim.datacenters.DatacenterSimple;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.hosts.power.PowerHostSimple;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.allocationpolicies.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.predicates.PredicateType;
import org.cloudbus.cloudsim.resources.FileStorage;

/**
 * PowerDatacenter is a class that enables simulation of power-aware data
 * centers.
 *
 * <br>If you are using any algorithms, policies or workload included in the
 * power package please cite the following paper:<br>
 *
 * <ul>
 * <li><a href="http://dx.doi.org/10.1002/cpe.1867">Anton Beloglazov, and
 * Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of
 * Virtual Machines in Cloud Data Centers", Concurrency and Computation:
 * Practice and Experience (CCPE), Volume 24, Issue 13, Pages: 1397-1420, John
 * Wiley & Sons, Ltd, New York, USA, 2012</a>
 * </ul>
 *
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 2.0
 */
public class PowerDatacenter extends DatacenterSimple {

    /**
     * The switches consumed power.
     */
    private double power;

    /**
     * Indicates if migrations are disabled or not.
     */
    private boolean disableMigrations;

    /**
     * The last time submitted cloudlets were processed.
     */
    private double cloudletSubmitted;

    /**
     * The VM migration count.
     */
    private int migrationCount;

    /**
     * Creates a PowerDatacenter.
     *
     * @param simulation The CloudSim instance that represents the simulation the Entity is related to
     * @param characteristics the characteristics of the switches to be created
     * @param vmAllocationPolicy the policy to be used to allocate VMs into hosts
     *
     */
    public PowerDatacenter(
        CloudSim simulation,
        DatacenterCharacteristics characteristics,
        VmAllocationPolicy vmAllocationPolicy)
    {
        super(simulation, characteristics, vmAllocationPolicy);
        setPower(0.0);
        setDisableMigrations(false);
        setCloudletSubmitted(-1);
        setMigrationCount(0);
    }

    /**
     * Creates a PowerDatacenter with the given parameters.
     *
     * @param simulation The CloudSim instance that represents the simulation the Entity is related to
     * @param characteristics the characteristics of the switches to be created
     * @param vmAllocationPolicy the policy to be used to allocate VMs into hosts
     * @param storageList a List of storage elements, for data simulation
     * @param schedulingInterval the scheduling delay to process each switches received event
     *
     * @deprecated Use the other available constructors with less parameters
     * and set the remaining ones using the respective setters.
     * This constructor will be removed in future versions.
     */
    @Deprecated
    public PowerDatacenter(
        CloudSim simulation,
        DatacenterCharacteristics characteristics,
        VmAllocationPolicy vmAllocationPolicy,
        List<FileStorage> storageList,
        double schedulingInterval)
    {
        this(simulation, characteristics, vmAllocationPolicy);
        setStorageList(storageList);
        setSchedulingInterval(schedulingInterval);
    }

    @Override
    protected void updateCloudletProcessing() {
        if (getCloudletSubmitted() == -1 || getCloudletSubmitted() == getSimulation().clock()) {
            getSimulation().cancelAll(getId(), new PredicateType(CloudSimTags.VM_UPDATE_CLOUDLET_PROCESSING_EVENT));
            schedule(getId(), getSchedulingInterval(), CloudSimTags.VM_UPDATE_CLOUDLET_PROCESSING_EVENT);
            return;
        }
        double currentTime = getSimulation().clock();

        // if some time passed since last processing
        if (currentTime > getLastProcessTime()) {
            System.out.print(currentTime + " ");

            double minTime = updateCloudetProcessingWithoutSchedulingFutureEventsForce();

            if (!isDisableMigrations()) {
                Map<Vm, Host> migrationMap =
                        getVmAllocationPolicy().optimizeAllocation(getVmList());

                for (Entry<Vm, Host> migrate : migrationMap.entrySet()) {
                    Host targetHost = migrate.getValue();
                    Host oldHost = migrate.getKey().getHost();

                    if (oldHost == null) {
                        Log.printFormattedLine(
                            "%.2f: Migration of VM #%d to Host #%d is started",
                            currentTime, migrate.getKey().getId(), targetHost.getId());
                    } else {
                        Log.printFormattedLine(
                            "%.2f: Migration of VM #%d from Host #%d to Host #%d is started",
                            currentTime, migrate.getKey().getId(),
                            oldHost.getId(), targetHost.getId());
                    }

                    targetHost.addMigratingInVm(migrate.getKey());
                    incrementMigrationCount();

                    /**
                     * VM migration delay = RAM / bandwidth *
                     */
                                            // we use BW / 2 to model BW available for migration purposes, the other
                    // half of BW is for VM communication
                    // around 16 seconds for 1024 MB using 1 Gbit/s network
                    send(
                        getId(),
                        migrate.getKey().getRam() / ((double) targetHost.getBwCapacity() / (2 * 8000)),
                        CloudSimTags.VM_MIGRATE, migrate);
                }
            }

            // schedules an event to the next time
            if (minTime != Double.MAX_VALUE) {
                getSimulation().cancelAll(getId(), new PredicateType(CloudSimTags.VM_UPDATE_CLOUDLET_PROCESSING_EVENT));
                send(getId(), getSchedulingInterval(), CloudSimTags.VM_UPDATE_CLOUDLET_PROCESSING_EVENT);
            }

            setLastProcessTime(currentTime);
        }
    }

    /**
     * Update cloudet processing without scheduling future events.
     *
     * @return the double
     * @see #updateCloudetProcessingWithoutSchedulingFutureEventsForce()
     * @todo There is an inconsistence in the return value of this method with
     * return value of similar methods such as
     * {@link #updateCloudetProcessingWithoutSchedulingFutureEventsForce()},
     * that returns {@link Double#MAX_VALUE} by default. The current method
     * returns 0 by default.
     */
    protected double updateCloudetProcessingWithoutSchedulingFutureEvents() {
        if (getSimulation().clock() > getLastProcessTime()) {
            return updateCloudetProcessingWithoutSchedulingFutureEventsForce();
        }
        return 0;
    }

    /**
     * Update cloudet processing without scheduling future events.
     *
     * @return expected time of completion of the next cloudlet in all VMs of
     * all hosts or {@link Double#MAX_VALUE} if there is no future events
     * expected in this host
     */
    protected double updateCloudetProcessingWithoutSchedulingFutureEventsForce() {
        double currentTime = getSimulation().clock();
        double minTime = Double.MAX_VALUE;
        double timeDiff = currentTime - getLastProcessTime();
        double timeFrameDatacenterEnergy = 0.0;

        Log.printLine("\n\n--------------------------------------------------------------\n\n");
        Log.printFormattedLine("New resource usage for the time frame starting at %.2f:", currentTime);

        for (PowerHostSimple host : this.<PowerHostSimple>getHostList()) {
            Log.printLine();

            double time = host.updateVmsProcessing(currentTime); // inform VMs to update processing
            if (time < minTime) {
                minTime = time;
            }

            Log.printFormattedLine(
                    "%.2f: [Host #%d] utilization is %.2f%%",
                    currentTime,
                    host.getId(),
                    host.getUtilizationOfCpu() * 100);
        }

        if (timeDiff > 0) {
            Log.printFormattedLine(
                    "\nEnergy consumption for the last time frame from %.2f to %.2f:",
                    getLastProcessTime(),
                    currentTime);

            for (PowerHostSimple host : this.<PowerHostSimple>getHostList()) {
                double previousUtilizationOfCpu = host.getPreviousUtilizationOfCpu();
                double utilizationOfCpu = host.getUtilizationOfCpu();
                double timeFrameHostEnergy = host.getEnergyLinearInterpolation(
                        previousUtilizationOfCpu,
                        utilizationOfCpu,
                        timeDiff);
                timeFrameDatacenterEnergy += timeFrameHostEnergy;

                Log.printLine();
                Log.printFormattedLine(
                        "%.2f: [Host #%d] utilization at %.2f was %.2f%%, now is %.2f%%",
                        currentTime,
                        host.getId(),
                        getLastProcessTime(),
                        previousUtilizationOfCpu * 100,
                        utilizationOfCpu * 100);
                Log.printFormattedLine(
                        "%.2f: [Host #%d] energy is %.2f W*sec",
                        currentTime,
                        host.getId(),
                        timeFrameHostEnergy);
            }

            Log.printFormattedLine(
                    "\n%.2f: Data center's energy is %.2f W*sec\n",
                    currentTime,
                    timeFrameDatacenterEnergy);
        }

        setPower(getPower() + timeFrameDatacenterEnergy);

        checkCloudletsCompletionForAllHosts();

        /**
         * Remove completed VMs *
         */
        for (PowerHostSimple host : this.<PowerHostSimple>getHostList()) {
            for (Vm vm : host.getCompletedVms()) {
                getVmAllocationPolicy().deallocateHostForVm(vm);
                getVmList().remove(vm);
                Log.printLine("VM #" + vm.getId() + " has been deallocated from host #" + host.getId());
            }
        }

        Log.printLine();

        setLastProcessTime(currentTime);
        return minTime;
    }

    @Override
    protected void processVmMigrate(SimEvent ev, boolean ack) {
        updateCloudetProcessingWithoutSchedulingFutureEvents();
        super.processVmMigrate(ev, ack);
        SimEvent event = getSimulation().findFirstDeferred(getId(), new PredicateType(CloudSimTags.VM_MIGRATE));
        if (event == null || event.eventTime() > getSimulation().clock()) {
            updateCloudetProcessingWithoutSchedulingFutureEventsForce();
        }
    }

    @Override
    protected void processCloudletSubmit(SimEvent ev, boolean ack) {
        super.processCloudletSubmit(ev, ack);
        setCloudletSubmitted(getSimulation().clock());
    }

    /**
     * Gets the power.
     *
     * @return the power
     */
    public double getPower() {
        return power;
    }

    /**
     * Sets the power.
     *
     * @param power the new power
     */
    protected final void setPower(double power) {
        this.power = power;
    }

    /**
     * Checks if PowerDatacenter is in migration.
     *
     * @return true, if PowerDatacenter is in migration; false otherwise
     */
    protected boolean isInMigration() {
        boolean result = false;
        for (Vm vm : getVmList()) {
            if (vm.isInMigration()) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * Checks if migrations are disabled.
     *
     * @return true, if migrations are disable; false otherwise
     */
    public boolean isDisableMigrations() {
        return disableMigrations;
    }

    /**
     * Disable or enable migrations.
     *
     * @param disableMigrations true to disable migrations; false to enable
     */
    public final PowerDatacenter setDisableMigrations(boolean disableMigrations) {
        this.disableMigrations = disableMigrations;
        return this;
    }

    /**
     * Checks if is cloudlet submited.
     *
     * @return true, if is cloudlet submited
     */
    protected double getCloudletSubmitted() {
        return cloudletSubmitted;
    }

    /**
     * Sets the cloudlet submitted.
     *
     * @param cloudletSubmitted the new cloudlet submited
     */
    protected final void setCloudletSubmitted(double cloudletSubmitted) {
        this.cloudletSubmitted = cloudletSubmitted;
    }

    /**
     * Gets the migration count.
     *
     * @return the migration count
     */
    public int getMigrationCount() {
        return migrationCount;
    }

    /**
     * Sets the migration count.
     *
     * @param migrationCount the new migration count
     */
    protected final void setMigrationCount(int migrationCount) {
        this.migrationCount = migrationCount;
    }

    /**
     * Increment migration count.
     */
    protected void incrementMigrationCount() {
        setMigrationCount(getMigrationCount() + 1);
    }

}