import com.gameexpert.bootstrap.WebcraftDefaults;
import java.util.Map;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

public class DefaultsSmoke {
    public static void main(String[] args) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("student", Map.of(
                "server.compression.enabled", "false", "spring.jpa.open-in-view", "true")));
        new WebcraftDefaults().postProcessEnvironment(environment, null);
        if (!"always".equals(environment.getProperty("spring.sql.init.mode"))) throw new AssertionError("SQL defaults missing");
        if (!"false".equals(environment.getProperty("spring.data.redis.repositories.enabled"))) throw new AssertionError("Redis default missing");
        if (!"false".equals(environment.getProperty("server.compression.enabled"))) throw new AssertionError("Student override lost");
        if (!"true".equals(environment.getProperty("spring.jpa.open-in-view"))) throw new AssertionError("JPA override lost");
        var defaults = environment.getPropertySources().get("webcraftEngineDefaults");
        for (String key : new String[] {"spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                "spring.data.redis.host", "spring.data.redis.port", "spring.jpa.hibernate.ddl-auto"}) {
            if (defaults.containsProperty(key)) throw new AssertionError("Assignment answer supplied: " + key);
        }
        System.out.println("Engine defaults, student overrides and assignment boundary verified");
    }
}
