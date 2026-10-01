package com.receipttrust.assignment;

import com.receipttrust.security.CurrentUserService;
import com.receipttrust.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints for an assignee to confirm or decline item assignments made to them
 * by someone who is not yet their friend.
 */
@RestController
@RequestMapping("/api/assignments")
public class AssignmentConfirmationController {

    private final AssignmentService assignmentService;
    private final CurrentUserService currentUserService;

    public AssignmentConfirmationController(AssignmentService assignmentService,
                                            CurrentUserService currentUserService) {
        this.assignmentService = assignmentService;
        this.currentUserService = currentUserService;
    }

    /** Assignments awaiting the current user's confirmation. */
    @GetMapping("/pending")
    public List<AssignmentDtos.PendingAssignment> pending() {
        return assignmentService.pendingForAssignee(currentUserService.require());
    }

    @PostMapping("/{assignmentId}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@PathVariable Long assignmentId) {
        assignmentService.confirmAssignment(currentUserService.require(), assignmentId);
    }

    @PostMapping("/{assignmentId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable Long assignmentId) {
        assignmentService.declineAssignment(currentUserService.require(), assignmentId);
    }
}
