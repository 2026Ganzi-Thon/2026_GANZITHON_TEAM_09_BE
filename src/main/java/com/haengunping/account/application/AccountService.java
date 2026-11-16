package com.haengunping.account.application;
import com.haengunping.account.*;
import com.haengunping.account.domain.User;
import com.haengunping.account.infra.UserRepository;
import com.haengunping.common.error.ApiFault;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AccountService implements AccountQuery {
    private final UserRepository users;
    private final SessionService sessions;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    public AccountService(UserRepository users,SessionService sessions,ApplicationEventPublisher events,Clock clock){
        this.users=users;this.sessions=sessions;this.events=events;this.clock=clock;
    }
    public record Created(User user,String token) {}
    public static String nickname(String value){
        if(value==null || value.isBlank() || value.codePointCount(0,value.length())>10 || value.codePointCount(0,value.length())<1)
            throw ApiFault.validation("닉네임은 1~10자여야 해요");
        return value;
    }
    @Transactional
    public Created create(String nickname){
        String token=sessions.newToken();
        User user=users.save(new User(nickname(nickname),sessions.hash(token),clock.instant(),false));
        return new Created(user,token);
    }
    @Transactional(readOnly=true)
    public User get(long id){return users.findById(id).filter(User::isActive).orElseThrow(()->new ApiFault(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","로그인이 필요해요"));}
    @Transactional
    public User rename(long id,String name){
        User user=locked(id);name=nickname(name);
        user.rename(name);events.publishEvent(new NicknameChangedEvent(id,name));return user;
    }
    @Transactional
    public void consent(long id,boolean agreed){locked(id).consent(agreed,clock.instant());}
    @Transactional
    public void confirmAdult(long id,boolean confirmed){
        if(!confirmed)throw ApiFault.validation("확인 값을 true로 입력해 주세요");
        locked(id).confirmAdult();
    }
    @Transactional
    public void eraseHealthRecords(long id){ locked(id);events.publishEvent(new HealthRecordsDeletionRequestedEvent(id)); }
    @Transactional
    public void delete(long id){
        User user=locked(id);
        events.publishEvent(new AccountDeletedEvent(id));
        user.delete(clock.instant());
    }
    private User locked(long id){return users.lockById(id).filter(User::isActive).orElseThrow(()->new ApiFault(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","로그인이 필요해요"));}
    @Override @Transactional(readOnly=true) public boolean hasSensitiveConsent(long id){return users.findById(id).filter(User::isActive).map(User::hasSensitiveConsent).orElse(false);}
    @Override @Transactional(readOnly=true) public boolean isAdultConfirmed(long id){return users.findById(id).filter(User::isActive).map(User::isAdultConfirmed).orElse(false);}
    @Override @Transactional(readOnly=true) public boolean isActive(long id){return users.findById(id).filter(User::isActive).isPresent();}
    @Override @Transactional(readOnly=true) public String nicknameOf(long id){return get(id).getNickname();}
    @Override @Transactional(readOnly=true) public java.util.Optional<java.time.Instant> lastSeenAt(long id){return users.findById(id).filter(User::isActive).map(User::getLastSeenAt);}
    @Override @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY) public void lockForGroupChange(long id){locked(id);}
    @Override public void requireSensitiveConsent(long id){if(!hasSensitiveConsent(id))throw new ApiFault(HttpStatus.FORBIDDEN,"CONSENT_REQUIRED","민감정보 동의가 필요해요");}
}
