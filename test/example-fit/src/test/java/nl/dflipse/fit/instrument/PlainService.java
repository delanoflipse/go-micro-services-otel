package nl.dflipse.fit.instrument;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

public class PlainService implements InstrumentedService {
    public GenericContainer<?> service;
    public String name;

    public PlainService(String name, GenericContainer<?> service, Network network) {
        this.name = name;
        this.service = service;

        service.withNetwork(network);
        service.withNetworkAliases(name);
    }

    public GenericContainer<?> getContainer() {
        return service;
    }

    public String getName() {
        return name;
    }
}
