package com.brim.easyinvoice.model;

import org.hibernate.Hibernate;

import java.io.Serializable;

/**
 * Common base for entities: equality is by type + id (never by field values), and toString never
 * touches associations, so printing an entity can not trigger a lazy load.
 * A new, unsaved entity (id null) is only equal to itself.
 */
public abstract class BaseEntity<ID extends Serializable> {

    public abstract ID getId();

    @Override
    public final boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false;
        ID id = getId();
        return id != null && id.equals(((BaseEntity<?>) other).getId());
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();   // constant per type: stays stable when the id is assigned on save
    }

    @Override
    public String toString() {
        return Hibernate.getClass(this).getSimpleName() + "#" + getId();
    }
}
