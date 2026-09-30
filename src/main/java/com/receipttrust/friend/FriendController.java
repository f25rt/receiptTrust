package com.receipttrust.friend;

import com.receipttrust.security.CurrentUserService;
import com.receipttrust.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService friendService;
    private final CurrentUserService currentUserService;

    public FriendController(FriendService friendService, CurrentUserService currentUserService) {
        this.friendService = friendService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendDtos.FriendRequestResponse send(
            @Valid @RequestBody FriendDtos.FriendRequestCreate request) {
        User me = currentUserService.require();
        return friendService.sendRequestDto(me, request.addresseeUsername());
    }

    @GetMapping("/requests")
    public List<FriendDtos.FriendRequestResponse> incoming() {
        User me = currentUserService.require();
        return friendService.incomingRequestDtos(me);
    }

    @PostMapping("/requests/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable Long id) {
        friendService.accept(currentUserService.require(), id);
    }

    @PostMapping("/requests/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable Long id) {
        friendService.reject(currentUserService.require(), id);
    }

    @GetMapping
    public List<FriendDtos.UserSummary> friends() {
        User me = currentUserService.require();
        return friendService.listFriendDtos(me);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> remove(@PathVariable Long userId) {
        friendService.removeFriend(currentUserService.require(), userId);
        return ResponseEntity.noContent().build();
    }
}
