package com.gameexpert.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HeapAutoTuneTest {

    private static final long GB = 1024L * 1024 * 1024;
    private static final List<String> JAR_ARGS = List.of("-jar", "/app/app.jar");

    @Test
    void relaunchesWhenSmallContainerHeapWasLeftAtTheDefault() {
        assertThat(HeapAutoTune.shouldRelaunch(512 * 1024 * 1024L, 2 * GB, true, null, List.of(), JAR_ARGS)).isTrue();
    }

    @Test
    void leavesAnExplicitHeapAlone() {
        assertThat(HeapAutoTune.shouldRelaunch(512 * 1024 * 1024L, 2 * GB, true, null, List.of("-Xmx900m"), JAR_ARGS))
                .isFalse();
        assertThat(HeapAutoTune.shouldRelaunch(512 * 1024 * 1024L, 2 * GB, true, null,
                List.of("-XX:MaxRAMPercentage=45"), JAR_ARGS)).isFalse();
    }

    @Test
    void leavesADebuggedProcessAlone() {
        assertThat(HeapAutoTune.shouldRelaunch(512 * 1024 * 1024L, 2 * GB, true, null,
                List.of("-agentlib:jdwp=transport=dt_socket,server=y,address=5005"), JAR_ARGS)).isFalse();
    }

    @Test
    void staysOutsideContainersAndOutsideJarLaunches() {
        assertThat(HeapAutoTune.shouldRelaunch(512 * 1024 * 1024L, 2 * GB, false, null, List.of(), JAR_ARGS)).isFalse();
        assertThat(HeapAutoTune.shouldRelaunch(512 * 1024 * 1024L, 2 * GB, true, null, List.of(),
                List.of("-cp", "app", "com.gameexpert.Main"))).isFalse();
        assertThat(HeapAutoTune.shouldRelaunch(512 * 1024 * 1024L, 2 * GB, true, null, List.of(), null)).isFalse();
    }

    @Test
    void staysOnMachinesThatAreTooSmallOrLargeEnoughAlready() {
        assertThat(HeapAutoTune.shouldRelaunch(256 * 1024 * 1024L, GB, true, null, List.of(), JAR_ARGS)).isFalse();
        assertThat(HeapAutoTune.shouldRelaunch(2 * GB, 8 * GB, true, null, List.of(), JAR_ARGS)).isFalse();
        assertThat(HeapAutoTune.shouldRelaunch(GB, 2 * GB, true, null, List.of(), JAR_ARGS)).isFalse();
    }

    @Test
    void honoursTheOffSwitchThatTheChildProcessCarries() {
        assertThat(HeapAutoTune.shouldRelaunch(512 * 1024 * 1024L, 2 * GB, true, "false", List.of(), JAR_ARGS))
                .isFalse();
        assertThat(HeapAutoTune.childCommand(JAR_ARGS))
                .containsSubsequence(HeapAutoTune.HEAP_FLAG, "-D" + HeapAutoTune.SWITCH + "=false", "-jar",
                        "/app/app.jar");
    }
}
