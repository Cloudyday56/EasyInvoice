package com.brim.easyinvoice.web;

import com.brim.easyinvoice.audit.AuditHasher;
import com.brim.easyinvoice.audit.AuditService;
import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.UserStatus;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import com.brim.easyinvoice.security.AuthorizationService;
import com.brim.easyinvoice.security.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Audit endpoints must be admin-only at the API layer, not just hidden in the UI. */
@ExtendWith(MockitoExtension.class)
class AuditControllerAuthorizationTest {

    @Mock private CurrentUser currentUser;
    @Mock private AuditService audit;
    @Mock private ApprovalStepRepository approvalSteps;

    private AuditController controller;

    @BeforeEach
    void setUp() {
        controller = new AuditController(currentUser, audit, new AuthorizationService(approvalSteps, audit));
    }

    @Test
    void nonAdminCannotReadOrExportAnyAuditData() {
        when(currentUser.require("U5")).thenReturn(user("U5", Role.EMPLOYEE));

        assertThatThrownBy(() -> controller.latest("U5", 10)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.verify("U5")).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.export("U5", null)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> controller.export("U5", "INV-1")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void adminCanReadAndExportAuditData() {
        when(currentUser.require("U001")).thenReturn(user("U001", Role.ADMIN));
        when(audit.latest(10)).thenReturn(List.of());
        when(audit.verifyChain()).thenReturn(new AuditHasher.VerifyResult(true, 0, null, null));
        when(audit.exportCsv(null)).thenReturn("all");
        when(audit.exportCsv("INV-1")).thenReturn("csv");

        assertThat(controller.latest("U001", 10)).isEmpty();
        assertThat(controller.verify("U001").valid()).isTrue();
        assertThat(controller.export("U001", null).getBody()).isEqualTo("all");
        assertThat(controller.export("U001", "INV-1").getBody()).isEqualTo("csv");
    }

    private static User user(String id, Role role) {
        User user = new User();
        user.setId(id);
        user.setFullName(id);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
