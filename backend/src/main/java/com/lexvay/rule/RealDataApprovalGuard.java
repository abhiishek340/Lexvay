package com.lexvay.rule;

import com.lexvay.common.ConflictException;
import com.lexvay.tenant.DataMode;
import com.lexvay.tenant.TenantRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Keeps fictional rules out of real data (D-561): in a LIVE tenant a rule version's requirements are approved only
 * when the reviewer recorded the basis of its dates and that basis does not say it is fictional demo data. A DEMO
 * tenant approves as before. The basis is the current one of the version (RG-1: the newest {@code rule_version_status}
 * row, or the creation value while there is none), so a version without one is refused here and a correction that
 * states a real source makes it approvable.
 */
@Component
class RealDataApprovalGuard {

    private final TenantRepository tenants;

    RealDataApprovalGuard(TenantRepository tenants) {
        this.tenants = tenants;
    }

    void require(RuleVersion version) {
        if (tenants.currentDataMode() == DataMode.DEMO) {
            return;
        }
        String basis = Optional.ofNullable(version.legal().datesBasis()).orElseThrow(() -> new ConflictException(
                "Requirements cannot be approved for real data until the basis of the rule version's dates is recorded"));
        if (basis.toLowerCase(Locale.ROOT).contains(DataMode.DEMO_NOTICE.toLowerCase(Locale.ROOT))) {
            throw new ConflictException("Requirements cannot be approved for real data from a rule version whose dates are fictional demo data");
        }
    }
}
