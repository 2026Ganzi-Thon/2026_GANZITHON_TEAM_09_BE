package com.haengunping.group.api;
import com.haengunping.common.security.CurrentUser;
import com.haengunping.group.api.dto.ReportRequest;
import com.haengunping.group.application.GroupModerationService;
import com.haengunping.group.application.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/groups/{groupId}/members")
public class GroupMemberController {
    private final GroupService groups;
    private final GroupModerationService moderation;
    public GroupMemberController(GroupService groups,GroupModerationService moderation){this.groups=groups;this.moderation=moderation;}
    @DeleteMapping("/me") public ResponseEntity<Void> leave(@PathVariable long groupId){
        groups.leave(CurrentUser.id(),groupId);return ResponseEntity.noContent().build();
    }
    @PostMapping("/{memberId}/block") public ResponseEntity<Void> block(@PathVariable long groupId,@PathVariable long memberId){
        moderation.block(CurrentUser.id(),groupId,memberId);return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/{memberId}/block") public ResponseEntity<Void> unblock(@PathVariable long groupId,@PathVariable long memberId){
        moderation.unblock(CurrentUser.id(),groupId,memberId);return ResponseEntity.noContent().build();
    }
    @PostMapping("/{memberId}/reports") public ResponseEntity<Void> report(@PathVariable long groupId,@PathVariable long memberId,
            @RequestBody @Valid ReportRequest request){
        moderation.report(CurrentUser.id(),groupId,memberId,request.reason());return ResponseEntity.noContent().build();
    }
}
