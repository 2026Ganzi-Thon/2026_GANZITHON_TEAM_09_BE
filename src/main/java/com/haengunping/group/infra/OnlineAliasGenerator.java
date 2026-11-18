package com.haengunping.group.infra;
import java.security.SecureRandom;
import java.util.Set;
import org.springframework.stereotype.Component;
@Component
public class OnlineAliasGenerator {
    private static final String[] ADJECTIVES={"산책하는","졸린","기분 좋은","신나는","차분한","다정한","배고픈","용감한","호기심 많은","느긋한","활발한","조용한"};
    private static final String[] ANIMALS={"수달","고양이","토끼","판다","강아지","고래","부엉이","여우","햄스터","거북이","펭귄","다람쥐"};
    private final SecureRandom random=new SecureRandom();
    public String next(Set<String> taken){
        for(int n=0;n<200;n++){
            String value=ADJECTIVES[random.nextInt(ADJECTIVES.length)]+" "+ANIMALS[random.nextInt(ANIMALS.length)];
            if(value.length()<=20 && !taken.contains(value))return value;
        }
        throw new IllegalStateException("온라인 별칭을 생성할 수 없습니다");
    }
    public String groupName(){
        String[] names={"아침 산책단","작은 응원단","햇살 모임","하루 한 걸음","느긋한 탐험단"};
        return names[random.nextInt(names.length)];
    }
}
