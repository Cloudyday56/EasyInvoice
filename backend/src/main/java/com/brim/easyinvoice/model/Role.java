package com.brim.easyinvoice.model;

/** The three roles. users.source_role keeps the original role from the dataset (finance_manager vs controller_admin...). */
public enum Role {
    EMPLOYEE,  // requester: uploads invoices, sees own submissions
    APPROVER,  // department manager: approves own department's invoices up to a limit
    ADMIN      // finance manager and controller: approves higher tiers, releases payments, sees everything
}
