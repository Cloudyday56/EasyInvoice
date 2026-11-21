package com.brim.easyinvoice.audit;

import com.brim.easyinvoice.model.User;

/** Who did something: a person (optionally acting for someone on leave) or the pipeline itself. */
public record Actor(String id, String role, String actingFor) {

    public static final Actor SYSTEM = new Actor("system", "SYSTEM", null);

    public static Actor of(User user) {
        return new Actor(user.id(), user.role().name(), null);
    }

    public static Actor of(User user, String actingFor) {
        return new Actor(user.id(), user.role().name(), actingFor);
    }
}
