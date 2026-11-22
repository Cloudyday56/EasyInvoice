package com.brim.easyinvoice.web;

import com.brim.easyinvoice.payments.CardRedemptionService;
import com.brim.easyinvoice.security.Action;
import com.brim.easyinvoice.security.AuthorizationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Demo card-network simulator. It validates real card rules and records the result in the audit log. */
@RestController
@RequestMapping("/api/payments")
public class CardRedemptionController {

    public record RedeemRequest(String scenario, String merchantId, BigDecimal amount) {
    }

    private final CurrentUser currentUser;
    private final AuthorizationService authorization;
    private final CardRedemptionService redemption;

    public CardRedemptionController(CurrentUser currentUser, AuthorizationService authorization,
                                    CardRedemptionService redemption) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.redemption = redemption;
    }

    @PostMapping("/{paymentId}/card/redeem")
    public CardRedemptionService.Redemption redeem(
            @RequestHeader(value = CurrentUser.HEADER, required = false) String userId,
            @PathVariable String paymentId,
            @RequestBody(required = false) RedeemRequest body) {
        var user = currentUser.require(userId);
        authorization.authorize(user, Action.RUN_ADMIN_TOOLS, null);
        return redemption.redeem(user, paymentId,
                body == null ? null : body.scenario(),
                body == null ? null : body.merchantId(),
                body == null ? null : body.amount());
    }
}
