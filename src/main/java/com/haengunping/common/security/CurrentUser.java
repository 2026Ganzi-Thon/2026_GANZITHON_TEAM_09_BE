package com.haengunping.common.security;
//import com.haengunping.common.error.ApiFault;
import com.haengunping.common.error.ApiException;
import com.haengunping.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
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
}
