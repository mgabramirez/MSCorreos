package com.acosux.MSCorreos.dao;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import org.hibernate.Session;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación genérica de DAO usando JPA EntityManager (Hibernate 6 compatible).
 *
 * <p>Esta clase reemplaza la implementación anterior basada en Hibernate 4 Session API
 * (Criteria, Query, createSQLQuery) que fue eliminada en Hibernate 6.</p>
 *
 * <p>Nota: Esta clase es código legado. El nuevo código usa Spring Data JPA Repositories
 * directamente. Se mantiene para compatibilidad con código existente.</p>
 */
public class GenericDaoImpl<T, K extends Serializable> implements GenericDao<T, K> {

    @PersistenceContext
    protected EntityManager entityManager;

    @Override
    @Transactional
    public void saveOrUpdate(T t) {
        K id = obtenerIdValue(t);
        if (id == null || entityManager.find(t.getClass(), id) == null) {
            entityManager.persist(t);
        } else {
            entityManager.merge(t);
        }
    }

    @Override
    @Transactional
    public void saveOrUpdate(List<T> list) {
        for (T t : list) {
            saveOrUpdate(t);
        }
    }

    @Override
    @Transactional
    public void insertar(T t) {
        entityManager.persist(t);
    }

    @Override
    @Transactional
    public void insertar(List<T> listInsertar) {
        for (T t : listInsertar) {
            entityManager.persist(t);
        }
    }

    @Override
    @Transactional
    public void actualizar(T t) {
        entityManager.merge(t);
    }

    @Override
    @Transactional
    public void actualizar(List<T> listModificar) {
        for (T t : listModificar) {
            entityManager.merge(t);
        }
    }

    @Override
    @Transactional
    public void eliminar(T t) {
        T managed = entityManager.contains(t) ? t : entityManager.merge(t);
        entityManager.remove(managed);
    }

    @Override
    @Transactional
    public void eliminarPorId(Class<T> type, K id) {
        T t = entityManager.find(type, id);
        if (t != null) {
            entityManager.remove(t);
        }
    }

    @Override
    public void evict(T t) {
        entityManager.detach(t);
    }

    @Override
    public Object contar(Class<T> type) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        cq.select(cb.count(cq.from(type)));
        return entityManager.createQuery(cq).getSingleResult();
    }

    @Override
    public Object contar(String consulta, Object[] valores) {
        Query query = entityManager.createQuery(consulta);
        if (valores != null) {
            for (int i = 0; i < valores.length; i++) {
                query.setParameter(String.valueOf(i + 1), valores[i]);
            }
        }
        return query.getSingleResult();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> obtenerTodos(Class<T> type) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(type);
        cq.from(type);
        return entityManager.createQuery(cq).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> obtenerTodosOrder(Class<T> type, String atributoOrden) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(type);
        Root<T> root = cq.from(type);
        cq.orderBy(cb.asc(root.get(atributoOrden)));
        return entityManager.createQuery(cq).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> obtener(Class<T> type, String atributoOrden, Boolean activo) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(type);
        Root<T> root = cq.from(type);
        if (activo != null) {
            cq.where(cb.equal(root.get("activo"), activo));
        }
        cq.orderBy(cb.asc(root.get(atributoOrden)));
        return entityManager.createQuery(cq).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> obtenerListaPorAtributo(Class<T> type, String atributo, String valorAtributo, Boolean activo) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(type);
        Root<T> root = cq.from(type);
        if (activo != null) {
            cq.where(cb.equal(root.get(atributo), valorAtributo),
                     cb.equal(root.get("activo"), activo));
        } else {
            cq.where(cb.equal(root.get(atributo), valorAtributo));
        }
        return entityManager.createQuery(cq).getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T obtenerPorAtributo(Class<T> type, String atributo, String valorAtributo, Boolean activo) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(type);
        Root<T> root = cq.from(type);
        if (activo != null) {
            cq.where(cb.equal(root.get(atributo), valorAtributo),
                     cb.equal(root.get("activo"), activo));
        } else {
            cq.where(cb.equal(root.get(atributo), valorAtributo));
        }
        List<T> results = entityManager.createQuery(cq).setMaxResults(1).getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> obtenerPorHql(String consulta, Object[] valores) {
        Query query = entityManager.createQuery(consulta);
        if (valores != null) {
            for (int i = 0; i < valores.length; i++) {
                if (valores[i] != null) {
                    query.setParameter(String.valueOf(i + 1), valores[i]);
                }
            }
        }
        return query.getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T obtenerObjetoPorHql(String consulta, Object[] valores) {
        Query query = entityManager.createQuery(consulta);
        if (valores != null) {
            for (int i = 0; i < valores.length; i++) {
                if (valores[i] != null) {
                    query.setParameter(String.valueOf(i + 1), valores[i]);
                }
            }
        }
        List<T> lista = query.getResultList();
        return lista == null || lista.isEmpty() ? null : lista.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> obtenerPorHql(String consulta, Object[] valores, int min, int max) {
        Query query = entityManager.createQuery(consulta)
                .setFirstResult(min)
                .setMaxResults(max);
        if (valores != null) {
            for (int i = 0; i < valores.length; i++) {
                query.setParameter(String.valueOf(i + 1), valores[i]);
            }
        }
        return query.getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> obtenerLista(String consulta, Object[] valoresConsulta, boolean mensaje,
            Object[] valoresInicializar) {
        Query query = entityManager.createQuery(consulta);
        if (valoresConsulta != null) {
            for (int i = 0; i < valoresConsulta.length; i++) {
                if (valoresConsulta[i] != null) {
                    query.setParameter(String.valueOf(i + 1), valoresConsulta[i]);
                }
            }
        }
        return query.getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public T obtenerObjetoPorSql(String consulta, Class<T> type) {
        List<T> lista = entityManager.createNativeQuery(consulta, type).getResultList();
        return lista == null || lista.isEmpty() ? null : lista.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> obtenerPorSql(String consulta, Class<T> type) {
        return entityManager.createNativeQuery(consulta, type).getResultList();
    }

    @Override
    @Transactional
    public T obtenerPorId(Class<T> type, K id) {
        return entityManager.find(type, id);
    }

    @Override
    @Transactional
    public T obtenerPorIdEvict(Class<T> type, K id) {
        T entidad = entityManager.find(type, id);
        if (entidad != null) {
            entityManager.detach(entidad);
        }
        return entidad;
    }

    /**
     * Retorna la sesión Hibernate subyacente.
     * Disponible para compatibilidad con código legado.
     */
    @Override
    public Session session() {
        return entityManager.unwrap(Session.class);
    }

    // =========================================================================
    // Helpers privados
    // =========================================================================

    @SuppressWarnings("unchecked")
    private K obtenerIdValue(T t) {
        try {
            Class<?> c = t.getClass();
            for (Field field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class) || field.isAnnotationPresent(EmbeddedId.class)) {
                    field.setAccessible(true);
                    return (K) field.get(t);
                }
            }
            for (Method method : c.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Id.class) || method.isAnnotationPresent(EmbeddedId.class)) {
                    return (K) method.invoke(t);
                }
            }
        } catch (Exception e) {
            // ignorar
        }
        return null;
    }
}
