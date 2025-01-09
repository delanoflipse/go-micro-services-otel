package nl.dflipse.fit.instrument;

import java.util.ArrayList;
import java.util.List;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class InstrumentedApp {
    public Network network;
    private List<InstrumentedService> services;
    public CollectorService collector;

    public InstrumentedApp() {
        this.network = Network.newNetwork();
        this.services = new ArrayList<InstrumentedService>();

        String collectorName = "instrumentation-collector";
        this.collector = new CollectorService(collectorName, network);
        this.services.add(collector);
    }

    public void addService(String serviceName, GenericContainer<?> service) {
        PlainService plainService = new PlainService(serviceName, service, network);
        this.services.add(plainService);
    }

    public void addService(InstrumentedService service) {
        this.services.add(service);
    }

    public void addInstrumentedService(String serviceName, GenericContainer<?> service, int port) {
        ProxyService proxyService = new ProxyService(serviceName, service, port, this);
        this.services.add(proxyService);
    }

    public InstrumentedService getServiceByName(String serviceName) {
        for (InstrumentedService service : this.services) {
            if (service.getName().equals(serviceName)) {
                return service;
            }
        }

        return null;
    }

    public GenericContainer<?> getContainerByName(String serviceName) {
        for (InstrumentedService service : this.services) {
            if (service.getName().equals(serviceName)) {
                return service.getContainer();
            }
        }

        return null;
    }

    public void start() {
        for (InstrumentedService service : this.services) {
            GenericContainer<?> container = service.getContainer();
            try {
                container.start();
            } catch (Exception e) {
                System.err.println("Failed to start container: " + container.getDockerImageName());
                e.printStackTrace();
            }
        }
    }

    public void stop() {
        for (InstrumentedService service : this.services) {
            GenericContainer<?> container = service.getContainer();
            if (container != null && container.isRunning()) {
                container.stop();
            }
        }
    }
}
