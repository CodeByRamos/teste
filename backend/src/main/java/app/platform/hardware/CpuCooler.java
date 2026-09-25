package app.platform.hardware;

import java.util.Set;

public record CpuCooler(
        ComponentInfo info,
        Set<String> sockets,
        Boolean waterCooled,
        Integer heightMm,
        Integer radiatorSizeMm,
        Boolean fanless,
        Integer fanCount) implements HardwareComponent {

    public CpuCooler {
        sockets = sockets == null ? Set.of() : Set.copyOf(sockets);
    }

    public boolean isAirCooler() {
        return Boolean.FALSE.equals(waterCooled);
    }
}
