package com.haengunping.help.application;

import com.haengunping.help.domain.HelpItem;
import com.haengunping.help.infra.HelpItemRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 도움받을 곳 전체 목록. 누구에게나 같은 목록을 준다.
 * 사용자 정보(나이·지역·하는 일)는 받지 않고, 거르기는 기기가 한다(HELP-03, D-05).
 */
@Service
public class HelpItemsService {

    private final HelpItemRepository repository;

    HelpItemsService(HelpItemRepository repository) {
        this.repository = repository;
    }

    public HelpItems all() {
        return new HelpItems(
                repository.lastUpdatedDate(),
                repository.lastSyncedAt().orElse(null),
                repository.findAllInDisplayOrder());
    }

    /**
     * @param version  목록이 마지막으로 바뀐 날(KST, yyyy-MM-dd)
     * @param syncedAt 공공서비스 API 동기화 마지막 성공 시각. 아직 없으면 null
     */
    public record HelpItems(String version, Instant syncedAt, List<HelpItem> items) {
    }
}
