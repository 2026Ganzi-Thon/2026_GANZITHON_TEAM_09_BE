package com.haengunping.group.application;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class OnlineQueueMatchingJob {
    private final OnlineMatchService service;
    public OnlineQueueMatchingJob(OnlineMatchService service){this.service=service;}
    @Scheduled(cron="0 * * * * *",zone="Asia/Seoul")
    public void everyMinute(){service.matchWaiting();}
}
