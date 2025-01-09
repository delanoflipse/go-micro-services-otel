package nl.dflipse.fit.instrument;

import java.io.File;
import java.nio.file.Path;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

public class ProxyService implements InstrumentedService {
    public GenericContainer<?> service;
    public GenericContainer<?> proxy;
    public String name;
    private String serviceHost;

    private static Path imagePath = new File("../..").toPath().resolve("services/proxy");
    public static ImageFromDockerfile image = new ImageFromDockerfile().withFileFromPath(".", imagePath);
    public static MountableFile proxyScript = MountableFile.forHostPath("../../services/proxy/proxy.py");

    public ProxyService(String name, GenericContainer<?> service, int port, InstrumentedApp app) {
        this.name = name;
        this.service = service;

        this.serviceHost = name + "-instrumented";
        this.proxy = new GenericContainer<>("mitmproxy/mitmproxy:latest")
                .withCopyFileToContainer(proxyScript, "/proxy.py")
                .withEnv("SERVICE_NAME", name)
                .withEnv("PROXY_TARGET", this.serviceHost)
                .withEnv("COLLECTOR_HOST", app.collector.getContainer().getHost())
                .withCommand(
                        "mitmproxy -p " + port + " -s /proxy.py -mode reverse:https://" + this.serviceHost + ":" + port)
                .withNetwork(app.network)
                .withNetworkAliases(name);

        service.withNetwork(app.network);
        service.withNetworkAliases(this.serviceHost);
    }

    public GenericContainer<?> getContainer() {
        return service;
    }

    public String getName() {
        return name;
    }
}
