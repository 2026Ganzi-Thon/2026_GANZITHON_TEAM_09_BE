package com.haengunping.group.infra;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
/** Serialize all online-queue writes on the single PostgreSQL database. */
@Component
public class OnlineMatchMutex {
    private final EntityManager em;
    public OnlineMatchMutex(EntityManager em){this.em=em;}
    public void lock(){em.createNativeQuery("select pg_advisory_xact_lock(2026111709::bigint)").getSingleResult();}
}
