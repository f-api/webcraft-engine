package com.gameexpert.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class SerialMailboxTest {

    @Test
    void lettheRunningTaskFinishInsteadOfInterruptingIt() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean finished = new AtomicBoolean();
        SerialMailbox mailbox = new SerialMailbox("test-finish", 4, failure -> { });
        assertThat(mailbox.offer(() -> {
            started.countDown();
            try {
                // 접속 처리의 데이터베이스 호출을 대신한다. 인터럽트되면 그 자리에서 실패한다.
                release.await();
            } catch (InterruptedException cut) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
                return;
            }
            finished.set(true);
        })).isTrue();
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        mailbox.close();
        Thread.sleep(200);

        assertThat(interrupted).isFalse();
        release.countDown();
        for (int wait = 0; wait < 100 && !finished.get(); wait++) Thread.sleep(20);
        assertThat(finished).as("닫는 중에도 진행 중인 작업은 끝난다").isTrue();
    }

    @Test
    void stopsAcceptingWorkAndEndsTheWorkerOnceClosed() throws Exception {
        SerialMailbox mailbox = new SerialMailbox("test-close", 4, failure -> { });
        mailbox.close();

        assertThat(mailbox.offer(() -> { })).isFalse();
        for (int wait = 0; wait < 100 && mailbox.running(); wait++) Thread.sleep(20);
        assertThat(mailbox.running()).as("대기 중이던 작업 스레드는 닫으면 끝난다").isFalse();
    }

    @Test
    void interruptsOnlyWhenTheTaskOutlastsTheGracePeriod() {
        assertThat(SerialMailbox.CLOSE_GRACE.toSeconds()).isBetween(5L, 60L);
    }
}
