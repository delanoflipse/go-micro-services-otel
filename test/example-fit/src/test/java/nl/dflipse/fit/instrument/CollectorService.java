package nl.dflipse.fit.instrument;

import java.io.File;
import java.nio.file.Path;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.ImageFromDockerfile;

public class CollectorService implements InstrumentedService {
    private GenericContainer<?> container;
    private String name;

    private static Path imagePath = new File("../..").toPath().resolve("services/collector");
    private static ImageFromDockerfile image = new ImageFromDockerfile().withFileFromPath(".", imagePath);

    public CollectorService(String name, Network network) {
        this.name = name;

        this.container = new GenericContainer<>(image)
                .withCommand("flask --app collector.py run --host=0.0.0.0")
                .withExposedPorts(5000)
                .withNetwork(network)
                .withNetworkAliases(name);
    }

    public GenericContainer<?> getContainer() {
        return container;
    }

    public String getName() {
        return name;
    }

    public void start() {
        container.start();
    }

    public void stop() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }
}
