package app.platform;

import app.platform.config.DatabaseUrl;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PlatformApiApplication {

    public static void main(String[] args) {
        DatabaseUrl.springProperties(System.getenv()).forEach(System::setProperty);
        SpringApplication.run(PlatformApiApplication.class, args);
    }
}
