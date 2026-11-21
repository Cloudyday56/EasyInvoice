package com.brim.easyinvoice.model;

/** The three simplified roles. users.source_role keeps the original role from the dataset. */
public enum Role {
    EMPLOYEE,  // requester: uploads invoices, sees own submissions
    APPROVER,  // department manager: approves own department's invoices up to a limit
    ADMIN      // AP clerk + finance manager + controller combined
}
