package com.causa.rca.service;

import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Service responsible for managing scheduled RCA analysis tasks.
 * <p>
 * This service handles the periodic execution of workload scanning when the system
 * is configured in MONITORING mode. It provides a clean separation between scheduling
 * logic and the actual scanning implementation.
 * </p>
 * <p>
 * The scheduler is conditionally enabled based on the {@code rca.mode} configuration:
 * <ul>
 *   <li><b>MONITORING mode:</b> Scheduler runs at configured intervals</li>
 *   <li><b>ALERT_DRIVEN mode:</b> Scheduler is disabled</li>
 * </ul>
 * </p>
 * <p>
 * This modular design allows for:
 * <ul>
 *   <li>Easy testing of scheduling logic in isolation</li>
 *   <li>Simple modification of scheduling behavior</li>
 *   <li>Clear separation of concerns</li>
 * </ul>
 * </p>
 *
 * @see ScannerService
 * @see StartupService
 */
@ApplicationScoped
public class SchedulerService {

    private static final Logger LOG = Logger.getLogger(SchedulerService.class);

    @Inject
    ScannerService scannerService;

    @ConfigProperty(name = "rca.mode", defaultValue = "MONITORING")
    String rcaMode;

    /**
     * Scheduled task that periodically triggers workload scanning.
     * <p>
     * Runs at the interval specified by the {@code rca.scan.interval} configuration property,
     * with an initial delay of 2 minutes after application startup. This delay allows the
     * system to fully initialize before starting analysis.
     * </p>
     * <p>
     * The scheduler is automatically disabled when {@code rca.mode} is set to ALERT_DRIVEN,
     * using the {@link SkipIfNotMonitoringMode} predicate.
     * </p>
     *
     * @see ScannerService#scanWorkloads()
     */
    @Scheduled(every = "${rca.scan.interval}", delayed = "2m", skipExecutionIf = SchedulerService.SkipIfNotMonitoringMode.class)
    public void triggerScheduledScan() {
        LOG.debug("Scheduled scan triggered by SchedulerService");
        scannerService.scanWorkloads();
    }

    /**
     * Predicate to conditionally skip scheduled execution based on RCA mode.
     * <p>
     * This predicate is evaluated before each scheduled execution. When the system
     * is configured in ALERT_DRIVEN mode, the predicate returns {@code true}, causing
     * the scheduler to skip execution.
     * </p>
     * <p>
     * This approach provides a clean way to disable scheduling without removing the
     * {@code @Scheduled} annotation or using complex conditional logic.
     * </p>
     */
    public static class SkipIfNotMonitoringMode implements Scheduled.SkipPredicate {
        
        @ConfigProperty(name = "rca.mode", defaultValue = "MONITORING")
        String rcaMode;

        /**
         * Tests whether the scheduled execution should be skipped.
         *
         * @param execution the scheduled execution context (not used in this implementation)
         * @return {@code true} if the mode is not MONITORING, {@code false} otherwise
         */
        @Override
        public boolean test(ScheduledExecution execution) {
            boolean skip = !"MONITORING".equalsIgnoreCase(rcaMode);
            if (skip) {
                Logger.getLogger(SchedulerService.class).debug(
                    "Skipping scheduled scan - RCA mode is: " + rcaMode);
            }
            return skip;
        }
    }

    /**
     * Gets the current RCA mode configuration.
     * <p>
     * Useful for testing and monitoring purposes.
     * </p>
     *
     * @return the current RCA mode (MONITORING or ALERT_DRIVEN)
     */
    public String getRcaMode() {
        return rcaMode;
    }

    /**
     * Checks if the scheduler is currently enabled.
     * <p>
     * The scheduler is enabled only when the system is in MONITORING mode.
     * </p>
     *
     * @return {@code true} if scheduler is enabled, {@code false} otherwise
     */
    public boolean isSchedulerEnabled() {
        return "MONITORING".equalsIgnoreCase(rcaMode);
    }
}

// Made with Bob
