package beauty.beauty.global.aop;

import beauty.beauty.global.db.DataSourceContextHolder;
import beauty.beauty.global.db.DataSourceType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Aspect
@Component
@Order(0)
public class DataSourceAspect {

    // @Transactional(readOnly=true) → SLAVE, 그 외 → MASTER
    @Around("@annotation(transactional)")
    public Object routeByTransactional(ProceedingJoinPoint pjp,
                                       Transactional transactional) throws Throwable {
        DataSourceContextHolder.set(
                transactional.readOnly() ? DataSourceType.SLAVE : DataSourceType.MASTER
        );
        try {
            return pjp.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }

    // @ForceMaster → MASTER 강제 (SLAVE 덮어쓰기 방지)
    @Around("@annotation(beauty.beauty.global.aop.ForceMaster)")
    public Object forceToMaster(ProceedingJoinPoint pjp) throws Throwable {
        DataSourceContextHolder.forceMaster();
        try {
            return pjp.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}
