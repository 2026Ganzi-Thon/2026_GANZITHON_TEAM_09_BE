package com.haengunping.group;
import java.time.LocalDate;
import java.util.Map;
/** B가 구현할 그룹 성장·미션 조회 포트. 없으면 응답에서 today/character 필드를 생략한다. */
public interface GroupProgressSource {
    Map<String,Object> summary(long groupId,LocalDate today);
    Map<String,Object> detail(long groupId,LocalDate today);
}
