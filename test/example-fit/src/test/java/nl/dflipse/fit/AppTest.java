package nl.dflipse.fit;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

import nl.dflipse.fit.instrument.InstrumentedApp;
import nl.dflipse.fit.instrument.ProxyService;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Unit test for simple App.
 */
public class AppTest {

    static private InstrumentedApp app;

    @BeforeAll
    static public void setupServices() {

        app = new InstrumentedApp();

        // Add services
        Path rootPath = new File("../..").toPath();
        ImageFromDockerfile baseImage = new ImageFromDockerfile().withFileFromPath(".", rootPath);

        GenericContainer<?> geo = new GenericContainer<>(baseImage)
                .withCommand("go-micro-services geo");
        app.addInstrumentedService("geo", geo, 8080)
                .withHttp2();

        GenericContainer<?> rate = new GenericContainer<>(baseImage)
                .withCommand("go-micro-services rate");
        app.addInstrumentedService("rate", rate, 8080)
                .withHttp2();

        GenericContainer<?> search = new GenericContainer<>(baseImage)
                .withCommand("go-micro-services search")
                .dependsOn(geo, rate);
        app.addInstrumentedService("search", search, 8080)
                .withHttp2();

        GenericContainer<?> profile = new GenericContainer<>(baseImage)
                .withCommand("go-micro-services profile")
                .dependsOn(geo, rate);

        app.addInstrumentedService("profile", profile, 8080)
                .withHttp2();

        GenericContainer<?> frontend = new GenericContainer<>(baseImage)
                .withCommand("go-micro-services frontend")
                .dependsOn(search, profile);

        ProxyService proxiedFrontend = app.addInstrumentedService("frontend", frontend, 8080);
        proxiedFrontend.proxy.withExposedPorts(8080);

        GenericContainer<?> jaeger = new GenericContainer<>("jaegertracing/all-in-one:latest")
                .withExposedPorts(16686);
        app.addService("jaeger", jaeger);

        MountableFile otelCollectorConfig = MountableFile.forHostPath("../../otel-collector-config.yaml");
        GenericContainer<?> otelCollector = new GenericContainer<>(
                "otel/opentelemetry-collector-contrib:latest")
                .withCopyFileToContainer(otelCollectorConfig, "/otel-collector-config.yaml")
                .withCommand("--config=/otel-collector-config.yaml")
                .dependsOn(app.collector.getContainer(), jaeger);
        app.addService("otel-collector", otelCollector);

        // Start services
        app.start();
    }

    @AfterAll
    static public void teardownServices() {
        app.stop();
    }

    @Test
    public void testApp() {
        String access = "http://localhost:" + app.getContainerByName("frontend").getMappedPort(8080);
        assertTrue(true);
    }
}
