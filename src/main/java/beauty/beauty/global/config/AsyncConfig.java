package beauty.beauty.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * [After 2] @Async 스레드풀 설정
 *
 * @EnableAsync: @Async 어노테이션 활성화
 *
 * 스레드풀 파라미터 설명:
 *   corePoolSize  = 2   : 항상 유지되는 스레드 수
 *                         예약 생성이 동시에 2건 들어오면 각각 별도 스레드로 처리
 *   maxPoolSize   = 5   : 큐가 꽉 찼을 때 최대 확장 가능 스레드 수
 *   queueCapacity = 50  : 스레드가 모두 바쁠 때 대기시킬 작업 수
 *                         50건 초과 시 RejectedExecutionException 발생
 *   threadNamePrefix    : 로그에서 비동기 스레드를 식별하기 위한 이름 접두사
 *                         예) reservation-async-1, reservation-async-2
 *
 * 리소스 사이징 근거 (현재 프로젝트 규모):
 *   DAU ~500, Peak TPS ~5 가정
 *   예약 생성은 1초 이내 처리되므로 코어 2개면 충분
 *   운영 트래픽이 늘면 corePoolSize/maxPoolSize를 늘리거나
 *   Redis Streams / Kafka로 교체 권장
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "reservationTaskExecutor")
    public Executor reservationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("reservation-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
