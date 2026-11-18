package com.haengunping.group.api;
import com.haengunping.common.ratelimit.FixedWindowRateLimiter;
import com.haengunping.common.security.CurrentUser;
import com.haengunping.group.api.dto.GroupDetailResponse;
import com.haengunping.group.api.dto.InviteResponse;
import com.haengunping.group.application.InviteService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class InviteController {
    private final InviteService service;
    private final FixedWindowRateLimiter limiter;
    public InviteController(InviteService service,FixedWindowRateLimiter limiter){this.service=service;this.limiter=limiter;}
    @PostMapping("/groups/{groupId}/invites")
    public ResponseEntity<InviteResponse> create(@PathVariable long groupId){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(CurrentUser.id(),groupId));
    }
    @PostMapping("/invites/{code}/join")
    public ResponseEntity<GroupDetailResponse> join(@PathVariable String code,HttpServletRequest request){
        limiter.check("invite:"+request.getRemoteAddr(),20,Duration.ofHours(1));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.join(CurrentUser.id(),code));
    }
}
