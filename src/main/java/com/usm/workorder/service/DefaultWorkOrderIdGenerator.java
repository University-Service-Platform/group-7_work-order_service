package com.usm.workorder.service;

import com.usm.workorder.repository.WorkOrderRepository;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Produces IDs shaped like WO-2026-0001, mirroring service-request-service's
 * RequestIdGenerator (SR-2026-0001). Same prototype-grade limitation applies:
 * fine for a single dev instance, swap for a real sequence/UUID before
 * running more than one instance behind a load balancer.
 */
@Component
public class DefaultWorkOrderIdGenerator implements WorkOrderIdGenerator {

    private final AtomicLong counter;
    private volatile int currentYear;
    private final Object lock = new Object();

    public DefaultWorkOrderIdGenerator(WorkOrderRepository repository) {
        this.currentYear = Year.now().getValue();
        this.counter = new AtomicLong(repository.count());
    }

    @Override
    public String nextId() {
        int year = Year.now().getValue();
        synchronized (lock) {
            if (year != currentYear) {
                currentYear = year;
                counter.set(0);
            }
            long next = counter.incrementAndGet();
            return String.format("WO-%d-%04d", year, next);
        }
    }
}
