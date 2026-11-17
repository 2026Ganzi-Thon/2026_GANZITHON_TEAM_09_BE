package com.haengunping.account.application;
import com.haengunping.account.domain.User;
import com.haengunping.account.infra.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class SessionService {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final UserRepository users;
    private final Clock clock;
    public SessionService(UserRepository users,Clock clock){this.users=users;this.clock=clock;}
    public String newToken(){ byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public byte[] hash(String raw){
        try { return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.US_ASCII)); }
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @Transactional
    public Optional<Long> authenticate(String raw){
        if(raw==null || raw.length()!=43 || !raw.matches("[A-Za-z0-9_-]{43}"))return Optional.empty();
        return users.findByTokenHashAndDeletedAtIsNull(hash(raw)).filter(User::isActive).map(user->{user.touch(clock.instant());return user.getId();});
    }
}
