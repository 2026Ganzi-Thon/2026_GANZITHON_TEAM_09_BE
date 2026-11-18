package com.haengunping.common.security;
//import com.haengunping.common.error.ApiFault;
import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import java.util.OptionalLong;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
public final class CurrentUser {
    private CurrentUser() {}
    public static long id() {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Long value))
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        return value;
    }

    /** permitAll 경로용. 인증이 없거나 쿠키가 무효면 비어 있다(401을 던지지 않는다). */
    public static OptionalLong optionalId() {
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Long value))
            return OptionalLong.empty();
        return OptionalLong.of(value);
    }
}
