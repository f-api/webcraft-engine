package com.gameexpert.bootstrap;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 작은 인스턴스에서 -Xmx 없이 `java -jar` 로 띄웠을 때, 힙 상한만 올린 자식 JVM 으로 한 번
 * 다시 실행한다. 힙 크기는 JVM 이 시작한 뒤에는 바꿀 수 없어서, 실행 명령을 건드리지 않고
 * 힙을 키우려면 이 방법밖에 없다. 컨테이너 안에서 `java -jar` 로 뜬 경우에만 동작한다.
 */
public final class HeapAutoTune implements EnvironmentPostProcessor, Ordered {

    /** 자식 JVM 에 넘기는 힙 상한. 컨테이너 메모리 2GB 기준 약 780MB. */
    static final String HEAP_FLAG = "-XX:MaxRAMPercentage=40";
    /** 이 속성이 false 면 다시 띄우지 않는다. 자식 JVM 에도 같은 값이 전달된다. */
    static final String SWITCH = "webcraft.heap.autoTune";

    private static final long MIN_TOTAL_BYTES = 1_500L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024;
    private static boolean applied;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (applied) {
            return;
        }
        applied = true;
        List<String> arguments = currentArguments();
        if (!shouldRelaunch(Runtime.getRuntime().maxMemory(), totalMemoryBytes(), insideContainer(),
                System.getProperty(SWITCH), ManagementFactory.getRuntimeMXBean().getInputArguments(), arguments)) {
            return;
        }
        relaunch(childCommand(arguments));
    }

    /**
     * 자식 JVM 으로 다시 띄워야 하는지 판단한다. 판단에 쓰는 값은 모두 인자로 받아 시험할 수 있게 둔다.
     */
    static boolean shouldRelaunch(long maxHeapBytes, long totalMemoryBytes, boolean insideContainer,
            String switchValue, List<String> vmArguments, List<String> arguments) {
        if ("false".equalsIgnoreCase(switchValue)) {
            return false;
        }
        if (!insideContainer || arguments == null || !arguments.contains("-jar")) {
            return false;
        }
        if (totalMemoryBytes < MIN_TOTAL_BYTES || totalMemoryBytes > MAX_TOTAL_BYTES) {
            return false;
        }
        for (String argument : vmArguments) {
            if (argument.startsWith("-Xmx") || argument.startsWith("-XX:MaxRAM")
                    || argument.startsWith("-agentlib:jdwp") || argument.startsWith("-Xrunjdwp")) {
                return false;
            }
        }
        return maxHeapBytes < totalMemoryBytes * 42 / 100;
    }

    /** 원래 인자 앞에 힙 상한과 재진입 차단 속성만 덧붙인다. JVM 옵션은 앞에 와야 한다. */
    static List<String> childCommand(List<String> arguments) {
        List<String> command = new ArrayList<>(arguments.size() + 3);
        command.add(javaBinary());
        command.add(HEAP_FLAG);
        command.add("-D" + SWITCH + "=false");
        command.addAll(arguments);
        return command;
    }

    private static String javaBinary() {
        return ProcessHandle.current().info().command()
                .orElse(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
    }

    private static List<String> currentArguments() {
        return ProcessHandle.current().info().arguments().map(List::of).orElse(null);
    }

    private static boolean insideContainer() {
        return new File("/.dockerenv").exists() || new File("/run/.containerenv").exists();
    }

    private static long totalMemoryBytes() {
        var os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
            return sun.getTotalMemorySize();
        }
        return 0L;
    }

    private static void relaunch(List<String> command) {
        System.out.println("[webcraft] 힙 상한을 올려 다시 실행합니다: " + HEAP_FLAG
                + " (끄려면 -D" + SWITCH + "=false)");
        Process child;
        try {
            child = new ProcessBuilder(command).inheritIO().start();
        } catch (Exception failure) {
            System.out.println("[webcraft] 다시 실행하지 못해 기본 힙으로 계속합니다: " + failure);
            return;
        }
        Thread stopper = new Thread(() -> {
            child.destroy();
            try {
                child.waitFor();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, "webcraft-heap-relaunch-stop");
        Runtime.getRuntime().addShutdownHook(stopper);
        // 부모는 자식이 끝날 때까지 기다리기만 하므로, 기동에 쓴 메모리는 운영체제에 돌려준다.
        System.gc();
        int status;
        try {
            status = child.waitFor();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            child.destroy();
            status = 143;
        }
        Runtime.getRuntime().halt(status);
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
