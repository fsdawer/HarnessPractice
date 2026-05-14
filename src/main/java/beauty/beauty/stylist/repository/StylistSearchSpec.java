package beauty.beauty.stylist.repository;

import beauty.beauty.stylist.entity.StylistProfile;
import beauty.beauty.stylist.entity.StylistServiceItem;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class StylistSearchSpec {

    public static Specification<StylistProfile> build(
            String keyword, String district, String category,
            Integer minPrice, Integer maxPrice) {

        return (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> preds = new ArrayList<>();

            Join<Object, Object> salonJoin = null;

            if (keyword != null && !keyword.isBlank()) {
                String pat = "%" + keyword.trim() + "%";
                Join<Object, Object> userJoin = root.join("user", JoinType.LEFT);
                salonJoin = root.join("salon", JoinType.LEFT);
                preds.add(cb.or(cb.like(userJoin.get("name"), pat), cb.like(salonJoin.get("name"), pat)));
            }

            if (district != null && !district.isBlank()) {
                if (salonJoin == null) salonJoin = root.join("salon", JoinType.LEFT);
                preds.add(cb.equal(salonJoin.get("district"), district.trim()));
            }

            if (category != null || minPrice != null || maxPrice != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                Root<StylistServiceItem> item = sub.from(StylistServiceItem.class);
                List<Predicate> subPreds = new ArrayList<>();
                subPreds.add(cb.equal(item.get("stylistProfile").get("id"), root.get("id")));
                subPreds.add(cb.isTrue(item.get("isActive")));
                if (category != null && !category.isBlank())
                    subPreds.add(cb.equal(item.get("category"), category.trim()));
                if (minPrice != null)
                    subPreds.add(cb.ge(item.get("price"), minPrice));
                if (maxPrice != null)
                    subPreds.add(cb.le(item.get("price"), maxPrice));
                sub.select(item.get("stylistProfile").get("id"))
                   .where(cb.and(subPreds.toArray(new Predicate[0])));
                preds.add(cb.exists(sub));
            }

            return preds.isEmpty() ? cb.conjunction()
                    : cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
