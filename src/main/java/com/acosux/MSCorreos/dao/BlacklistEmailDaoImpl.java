package com.acosux.MSCorreos.dao;

import com.acosux.MSCorreos.entidades.BlacklistEmail;
import java.util.List;
import org.hibernate.Criteria;
import org.hibernate.criterion.Restrictions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Implementación del DAO para la tabla global blacklist_emails.
 */
@Repository
public class BlacklistEmailDaoImpl extends GenericDaoImpl<BlacklistEmail, Long> implements BlacklistEmailDao {
    
    @Override
    public BlacklistEmail findByEmail(String email) {
        return (BlacklistEmail) session().createCriteria(BlacklistEmail.class)
                .add(Restrictions.eq("email", email))
                .uniqueResult();
    }
    
    @Override
    public boolean existsByEmailAndActivoTrue(String email) {
        BlacklistEmail emailObj = (BlacklistEmail) session().createCriteria(BlacklistEmail.class)
                .add(Restrictions.eq("email", email))
                .add(Restrictions.eq("activo", true))
                .uniqueResult();
        return emailObj != null;
    }
    
    @Override
    public long countByActivoTrue() {
        Long count = (Long) session().createCriteria(BlacklistEmail.class)
                .add(Restrictions.eq("activo", true))
                .setProjection(org.hibernate.criterion.Projections.count("id"))
                .uniqueResult();
        return count != null ? count : 0;
    }
    
    @Override
    public Page<BlacklistEmail> findByActivoTrue(Pageable pageable) {
        // Get total count
        long total = countByActivoTrue();
        
        // Create criteria for active emails
        Criteria criteria = session().createCriteria(BlacklistEmail.class)
                .add(Restrictions.eq("activo", true));
        
        // Apply sorting
        if (pageable.getSort() != null) {
            pageable.getSort().forEach(order -> {
                String property = order.getProperty();
                if (order.isAscending()) {
                    criteria.addOrder(org.hibernate.criterion.Order.asc(property));
                } else {
                    criteria.addOrder(org.hibernate.criterion.Order.desc(property));
                }
            });
        } else {
            criteria.addOrder(org.hibernate.criterion.Order.desc("fechaBloqueo"));
        }
        
        // Apply pagination
        criteria.setFirstResult((int) pageable.getOffset());
        criteria.setMaxResults(pageable.getPageSize());
        
        @SuppressWarnings("unchecked")
        List<BlacklistEmail> content = criteria.list();
        
        return new PageImpl<>(content, pageable, total);
    }
}