package com.haengunping.common.ratelimit;
import com.haengunping.common.error.ApiFault;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
/** MVP 단일 서버용 인메모리 고정 윈도우. 멀티 인스턴스에서는 Redis 기반으로 교체한다. */
@Component
public class FixedWindowRateLimiter {
    private record Bucket(Instant start, int count) {}
    private final ConcurrentHashMap<String,Bucket> buckets=new ConcurrentHashMap<>();
    private final Clock clock;
    public FixedWindowRateLimiter(Clock clock) { this.clock=clock; }
    public void check(String key, int limit, Duration window) {
        Instant now=clock.instant();
        final long[] retry={0}; final boolean[] denied={false};
        buckets.compute(key,(k,b)->{
            if(b==null || !now.isBefore(b.start().plus(window))) return new Bucket(now,1);
            if(b.count()>=limit) { denied[0]=true; retry[0]=Math.max(1,Duration.between(now,b.start().plus(window)).toSeconds()); return b; }
            return new Bucket(b.start(),b.count()+1);
        });
        if(denied[0]) throw new ApiFault(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","잠시 후 다시 시도해 주세요",(int)Math.min(Integer.MAX_VALUE,retry[0]));
        if(buckets.size()>100_000) buckets.entrySet().removeIf(entry->!now.isBefore(entry.getValue().start().plus(window)));
    }
}
