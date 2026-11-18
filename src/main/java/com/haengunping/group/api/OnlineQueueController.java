package com.haengunping.group.api;
import com.haengunping.common.security.CurrentUser;
import com.haengunping.group.api.dto.MatchResponse;
import com.haengunping.group.application.OnlineMatchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/groups/online-queue")
public class OnlineQueueController {
    private final OnlineMatchService matching;
    public OnlineQueueController(OnlineMatchService matching){this.matching=matching;}
    @PostMapping public ResponseEntity<MatchResponse> enter(){
        var result=matching.enter(CurrentUser.id());
        return ResponseEntity.status("MATCHED".equals(result.status())?HttpStatus.CREATED:HttpStatus.ACCEPTED).body(result);
    }
    @DeleteMapping public ResponseEntity<Void> cancel(){matching.cancel(CurrentUser.id());return ResponseEntity.noContent().build();}
}
