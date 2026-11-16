package com.haengunping.group.api;
import com.haengunping.common.security.CurrentUser;
import com.haengunping.group.api.dto.*;
import com.haengunping.group.application.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/groups")
public class GroupController {
    private final GroupService groups;
    public GroupController(GroupService groups){this.groups=groups;}
    @GetMapping public GroupListResponse list(){return groups.list(CurrentUser.id());}
    @PostMapping public ResponseEntity<GroupDetailResponse> create(@RequestBody @Valid CreateGroupRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(groups.create(CurrentUser.id(),request));
    }
    @GetMapping("/{groupId}") public GroupDetailResponse detail(@PathVariable long groupId){return groups.detail(CurrentUser.id(),groupId);}
}
