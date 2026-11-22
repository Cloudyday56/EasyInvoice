package com.brim.easyinvoice.pipeline.nodes;

import com.brim.easyinvoice.model.ApprovalStep;
import com.brim.easyinvoice.model.Invoice;
import com.brim.easyinvoice.model.Role;
import com.brim.easyinvoice.model.User;
import com.brim.easyinvoice.model.UserStatus;
import com.brim.easyinvoice.pipeline.NodeContext;
import com.brim.easyinvoice.pipeline.Outcome;
import com.brim.easyinvoice.pipeline.StageResult;
import com.brim.easyinvoice.repo.ApprovalStepRepository;
import com.brim.easyinvoice.repo.DepartmentRepository;
import com.brim.easyinvoice.repo.InvoiceRepository;
import com.brim.easyinvoice.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** When the normal chain cannot be built, an Admin is assigned instead of the invoice getting stuck. */
class ApprovalRoutingFallbackTest {

    private static User admin(String id, String limit) {
        User u = new User();
        u.setId(id);
        u.setFullName(id);
        u.setRole(Role.ADMIN);
        u.setStatus(UserStatus.ACTIVE);
        u.setApprovalLimitCad(new BigDecimal(limit));
        return u;
    }

    @Test
    void invoiceWithNoDepartmentGoesToTheHighestLimitAdminWhoIsNotTheRequester() {
        User priya = admin("U001", "500000");
        User marcus = admin("U002", "250000");
        UserRepository users = mock(UserRepository.class);
        when(users.findByRoleAndStatus(Role.ADMIN, UserStatus.ACTIVE)).thenReturn(List.of(marcus, priya));
        ApprovalStepRepository steps = mock(ApprovalStepRepository.class);
        when(steps.forInvoice("EI-1")).thenReturn(List.of());
        ApprovalRoutingNode node = new ApprovalRoutingNode(mock(DepartmentRepository.class), steps, users,
                mock(InvoiceRepository.class));

        Invoice invoice = new Invoice();
        invoice.setId("EI-1");
        invoice.setTotalCad(new BigDecimal("4000"));
        invoice.setRequester(priya);   // no self-approval: Marcus must get it

        StageResult result = node.run(invoice, new NodeContext(null, 0));

        assertThat(result.outcome()).isEqualTo(Outcome.PASS);
        ArgumentCaptor<ApprovalStep> saved = ArgumentCaptor.forClass(ApprovalStep.class);
        verify(steps).save(saved.capture());
        assertThat(saved.getValue().getAssignedUserId()).isEqualTo("U002");
        assertThat(saved.getValue().getReason()).contains("No department");
    }
}
