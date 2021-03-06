package net.paoding.rose.jade.context.spring;

import net.paoding.rose.jade.annotation.DAO;
import net.paoding.rose.jade.dataaccess.DataSourceFactory;
import net.paoding.rose.jade.dataaccess.DataSourceHolder;
import net.paoding.rose.jade.statement.StatementMetaData;
import org.apache.commons.lang.IllegalClassException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 推荐使用 SpringDataSourceFactoryDelegate， 这样可以配置在第三方jar包中配置jade.dataSourceFactory 来替换SpringDataSourceFactory
 * @author 王志亮 [qieqie.wang@gmail.com]
 */
public class SpringDataSourceFactory implements DataSourceFactory, ApplicationContextAware {

    private Log logger = LogFactory.getLog(getClass());

    private ApplicationContext applicationContext;

    private ConcurrentHashMap<Class<?>, DataSourceHolder> cachedDataSources = new ConcurrentHashMap<Class<?>, DataSourceHolder>();

    public SpringDataSourceFactory() {
    }

    public SpringDataSourceFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public DataSourceHolder getHolder(StatementMetaData metaData,
            Map<String, Object> runtimeProperties) {
        Class<?> daoClass = metaData.getDAOMetaData().getDAOClass();
        DataSourceHolder holder = cachedDataSources.get(daoClass);
        if (holder != null) {
            return holder;
        }

        holder = getDataSourceByDirectory(daoClass, daoClass.getName());
        if (holder != null) {
            cachedDataSources.put(daoClass, holder);
            return holder;
        }
        String catalog = daoClass.getAnnotation(DAO.class).catalog();
        if (catalog.length() > 0) {
            holder = getDataSourceByDirectory(daoClass, catalog + "." + daoClass.getSimpleName());
        }
        if (holder != null) {
            cachedDataSources.put(daoClass, holder);
            return holder;
        }
        holder = getDataSourceByKey(daoClass, "jade.dataSource");
        if (holder != null) {
            cachedDataSources.put(daoClass, holder);
            return holder;
        }
        holder = getDataSourceByKey(daoClass, "dataSource");
        if (holder != null) {
            cachedDataSources.put(daoClass, holder);
            return holder;
        }
        return null;
    }

    private DataSourceHolder getDataSourceByDirectory(Class<?> daoClass, String catalog) {
        String tempCatalog = catalog;
        DataSourceHolder dataSource;
        while (tempCatalog != null && tempCatalog.length() > 0) {
            dataSource = getDataSourceByKey(daoClass, "jade.dataSource." + tempCatalog);
            if (dataSource != null) {
                return dataSource;
            }
            int index = tempCatalog.lastIndexOf('.');
            if (index == -1) {
                tempCatalog = null;
            } else {
                tempCatalog = tempCatalog.substring(0, index);
            }
        }
        return null;
    }

    private DataSourceHolder getDataSourceByKey(Class<?> daoClass, String key) {
        if (applicationContext.containsBean(key)) {
            Object dataSource = applicationContext.getBean(key);
            if (!(dataSource instanceof DataSource) && !(dataSource instanceof DataSourceFactory)) {
                throw new IllegalClassException("expects DataSource or DataSourceFactory, but a "
                        + dataSource.getClass().getName());
            }
            if (logger.isDebugEnabled()) {
                logger.debug("found dataSource: " + key + " for DAO " + daoClass.getName());
            }
            return new DataSourceHolder(dataSource);
        }
        return null;
    }
}
