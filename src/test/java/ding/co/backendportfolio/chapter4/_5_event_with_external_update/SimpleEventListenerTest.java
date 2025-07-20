package ding.co.backendportfolio.chapter4._5_event_with_external_update;

import ding.co.backendportfolio.config.IntegrationTest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@IntegrationTest
@Import({SimpleEventListenerTest.TestService.class, 
         SimpleEventListenerTest.TestEventListener.class,
         SimpleEventListenerTest.TestFacade.class})
class SimpleEventListenerTest {

    @Autowired
    private TestFacade testFacade;

    @Autowired
    private TestEventListener testEventListener;

    @Test
    @DisplayName("@EventListener는 트랜잭션이 없어도 정상 동작한다")
    void eventListenerWorksWithoutTransaction() throws InterruptedException {
        // given
        testEventListener.reset();

        // when
        testFacade.executeWithoutTransaction();

        // then
        boolean executed = testEventListener.waitForExecution(2, TimeUnit.SECONDS);
        assertThat(executed).isTrue();
        assertThat(testEventListener.isEventListenerExecuted()).isTrue();
        assertThat(testEventListener.isTransactionalEventListenerExecuted()).isFalse();
    }

    @Test
    @DisplayName("@TransactionalEventListener는 활성 트랜잭션이 필요하다")
    void transactionalEventListenerRequiresActiveTransaction() throws InterruptedException {
        // given
        testEventListener.reset();

        // when
        testFacade.executeWithTransaction();

        // then
        boolean executed = testEventListener.waitForExecution(2, TimeUnit.SECONDS);
        assertThat(executed).isTrue();
        assertThat(testEventListener.isEventListenerExecuted()).isTrue();
        assertThat(testEventListener.isTransactionalEventListenerExecuted()).isTrue();
    }

    @Test
    @DisplayName("현재 코드 구조에서 @TransactionalEventListener는 작동하지 않는다")
    void currentStructureDoesNotWorkWithTransactionalEventListener() throws InterruptedException {
        // given
        testEventListener.reset();

        // when
        testFacade.executeInDingcoStructure();

        // then
        testEventListener.waitForPossibleExecution(500);
        assertThat(testEventListener.isEventListenerExecuted()).isTrue();
        assertThat(testEventListener.isTransactionalEventListenerExecuted()).isFalse(); // 작동 안함!
    }

    @Component
    @RequiredArgsConstructor
    static class TestFacade {
        private final TestService testService;
        private final ApplicationEventPublisher eventPublisher;

        public void executeWithoutTransaction() {
            log.info("Facade method without transaction");
            eventPublisher.publishEvent("event without transaction");
        }

        @Transactional
        public void executeWithTransaction() {
            log.info("Facade method with transaction");
            eventPublisher.publishEvent("event with transaction");
        }

        // 현재 코드 구조와 동일
        public void executeInDingcoStructure() {
            log.info("Facade method - Current structure");
            // 1. 서비스 메서드 실행 (내부 트랜잭션)
            testService.executeWithTransaction();
            // 2. 이 시점에서 서비스의 트랜잭션은 이미 커밋됨
            // 3. 이벤트 발행 (활성 트랜잭션 없음)
            eventPublisher.publishEvent("event in dingco structure");
        }
    }

    @Service
    static class TestService {
        @Transactional
        public void executeWithTransaction() {
            log.info("Service method with transaction - will be committed after this method");
            // DB 작업 시뮬레이션
        }
    }

    @Component
    @Slf4j
    static class TestEventListener {
        private final AtomicBoolean eventListenerExecuted = new AtomicBoolean(false);
        private final AtomicBoolean transactionalEventListenerExecuted = new AtomicBoolean(false);
        private CountDownLatch latch = new CountDownLatch(1);

        @EventListener
        @Async
        public void handleEventListener(String event) {
            log.info("@EventListener executed: {}", event);
            eventListenerExecuted.set(true);
            latch.countDown();
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        @Async
        public void handleTransactionalEventListener(String event) {
            log.info("@TransactionalEventListener AFTER_COMMIT executed: {}", event);
            transactionalEventListenerExecuted.set(true);
        }

        public void reset() {
            eventListenerExecuted.set(false);
            transactionalEventListenerExecuted.set(false);
            latch = new CountDownLatch(1);
        }

        public boolean isEventListenerExecuted() {
            return eventListenerExecuted.get();
        }

        public boolean isTransactionalEventListenerExecuted() {
            return transactionalEventListenerExecuted.get();
        }

        public boolean waitForExecution(long timeout, TimeUnit unit) throws InterruptedException {
            return latch.await(timeout, unit);
        }

        public void waitForPossibleExecution(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    }
}