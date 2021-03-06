package com.education.service;

import com.education.common.utils.*;
import com.education.common.base.BaseMapper;
import com.education.common.cache.CacheBean;
import com.education.common.component.SpringBeanManager;
import com.education.common.constants.Constants;
import com.education.common.exception.BusinessException;
import com.education.common.model.AdminUserSession;
import com.education.common.model.FrontUserInfoSession;
import com.education.common.model.ModelBeanMap;
import com.education.service.task.TaskManager;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.apache.ibatis.session.RowBounds;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author zengjintao
 * @version 1.0
 * @create_at 2020/3/8 10:40
 */
public abstract class BaseService<M extends BaseMapper> {

    @Autowired
    protected M mapper;
    @Autowired
    protected TaskManager taskManager;
    @Autowired
    @Qualifier("redisCacheBean")
    protected CacheBean cacheBean;
    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String DEFAULT_MAPPER_PAGE_METHOD_NAME = "queryList";

    public List<ModelBeanMap> treeMenu() {
        List<ModelBeanMap> menuList = mapper.treeList();
        return MapTreeUtils.buildTreeData(menuList);
    }

    /**
     * 更新shiro 缓存中的用户信息，避免由于redis 缓存导致获取用户信息不一致问题
     * @param adminUserSession
     */
    public void updateShiroCacheUserInfo(AdminUserSession adminUserSession) {
        Subject subject = SecurityUtils.getSubject();
        PrincipalCollection principals = subject.getPrincipals();
        //realName认证信息的key，对应的value就是认证的user对象
        String realName = principals.getRealmNames().iterator().next();
        //创建一个PrincipalCollection对象
        PrincipalCollection newPrincipalCollection = new SimplePrincipalCollection(adminUserSession, realName);
        // 调用subject的runAs方法，把新的PrincipalCollection放到session里面
        subject.runAs(newPrincipalCollection);
    }

    /**
     * 分页查询
     * @param params
     * @return
     */
    public Result pagination(Map params) {
       return pagination(params, null, DEFAULT_MAPPER_PAGE_METHOD_NAME);
    }

    public Result paginationByCache(String cacheName, String key, Map params) {
        Result result = cacheBean.get(cacheName, key);
        if (ObjectUtils.isEmpty(result)) {
            result = pagination(params);
            cacheBean.put(cacheName, key, result);
        }
        return result;
    }

    public Result pagination(Map params, Class<? extends BaseMapper> mapperClass, String mapperMethod) {
        Result result = null;
        try {
            Integer offset = RowBounds.NO_ROW_OFFSET;
            Integer limit = RowBounds.NO_ROW_LIMIT;
            if (ObjectUtils.isNotEmpty(params.get("offset"))) {
                offset = Integer.valueOf((String) params.get("offset"));
            }

            if (ObjectUtils.isNotEmpty(params.get("limit"))) {
                limit = Integer.valueOf((String) params.get("limit"));
            }
            Page<ModelBeanMap> page = PageHelper.offsetPage(offset, limit);
            Object pageResult = null;
            if (ObjectUtils.isEmpty(mapperClass)) {
                pageResult = mapper.queryList(params);
            } else {
                Method method = mapperClass.getMethod(mapperMethod, Map.class);
                BaseMapper mapperBean = SpringBeanManager.getBean(mapperClass);
                pageResult = method.invoke(mapperBean, params);
            }
            ModelBeanMap dataMap = new ModelBeanMap();
            dataMap.put("dataList", pageResult);
            dataMap.put("total", page.getTotal());
            result = new Result(ResultCode.SUCCESS, dataMap);
        } catch (Exception e) {
            result = new Result(ResultCode.FAIL, "获取数据异常");
            logger.error("获取数据异常", e);
        }
        return result;
    }

    /**
     * 移除时间字段
     * @param params
     */
    protected void checkParams(Map params) {
        if (params != null) {
            params.remove("create_date");
            params.remove("last_login_time");
        }
    }

    public Result saveOrUpdate(boolean updateFlag, ModelBeanMap modelBeanMap) {
        try {
            String message = "";
            if (updateFlag) {
                checkParams(modelBeanMap);
                this.update(modelBeanMap);
                message = "修改";
            } else {
                modelBeanMap.put("create_date", new Date());
                this.save(modelBeanMap);
                message = "添加";
            }
            return new Result(ResultCode.SUCCESS, message + "成功");
        } catch (Exception e) {
            logger.error("操作异常", e);
            throw new BusinessException(new ResultCode(ResultCode.FAIL, "操作异常"));
        }
    }

    public Result saveOrUpdate(ModelBeanMap modelBeanMap) {
        boolean updateFlag = false;
        if (ObjectUtils.isNotEmpty(modelBeanMap.getInt("id"))) {
            updateFlag = true;
        }
        return this.saveOrUpdate(updateFlag, modelBeanMap);
    }


    public ResultCode deleteById(ModelBeanMap modelBeanMap) {
        return deleteById(modelBeanMap.getInt("id"));
    }

    public ResultCode deleteById(Integer id) {
        int result = mapper.deleteById(id);
        if (result > 0) {
            return new ResultCode(ResultCode.SUCCESS, "删除成功");
        }
        return new ResultCode(ResultCode.SUCCESS, "删除数据异常");
    }

    /**
     * 添加数据
     * @param modelMap
     * @return
     */
    public int save(Map modelMap) {
        return mapper.save(modelMap);
    }

    /**
     * 更新数据
     * @param modelMap
     * @return
     */
    public int update(Map modelMap) {
        return mapper.update(modelMap);
    }

    public List<Map> queryList(Map params) {
        return mapper.queryList(params);
    }


    public Map getAdminUser() {
        AdminUserSession adminSession = getAdminUserSession();
        if (ObjectUtils.isNotEmpty(adminSession)) {
            return adminSession.getUserMap();
        }
        return null;
    }

    /**
     * 是否体验账号
     * @return
     */
    public boolean isExperienceAccount() {
        Map userInfo = getFrontUserInfo();
        if (ObjectUtils.isNotEmpty(userInfo)) {
            return (boolean) userInfo.get("is_experience");
        }
        return false;
    }

    public Integer getAdminUserId() {
        if (ObjectUtils.isNotEmpty(getAdminUser())) {
            return (Integer) getAdminUser().get("id");
        }
        return null;
    }



    public Integer getUserId() {
        Map admin = getAdminUser();
        if (ObjectUtils.isNotEmpty(admin)) {
            return (Integer)admin.get("id");
        }
        return null;
    }

    /**
     * 获取管理员用户信息
     * @return
     */
    public AdminUserSession getAdminUserSession() {
        Subject subject = SecurityUtils.getSubject();
        AdminUserSession userSession = (AdminUserSession)subject.getPrincipal();
        if (ObjectUtils.isNotEmpty(userSession)) {
            return userSession;
        }
        return null;
    }


    /**
     * 获取前台用户信息
     * @return
     */
    public FrontUserInfoSession getFrontUserInfoSession() {
        String sessionId = RequestUtils.getCookieValue(Constants.SESSION_NAME);
        if (ObjectUtils.isEmpty(sessionId)) {
            return null;
        }
        return cacheBean.get(Constants.USER_INFO_CACHE, sessionId);
    }

    public Map getFrontUserInfo() {
        FrontUserInfoSession userInfoSession = getFrontUserInfoSession();
        if (ObjectUtils.isEmpty(userInfoSession)) {
            return null;
        }
        return userInfoSession.getUserInfoMap();
    }

    public FrontUserInfoSession getFrontUserInfoSession(String sessionId) {
        return cacheBean.get(Constants.USER_INFO_CACHE, sessionId);
    }

    public Integer getFrontUserInfoId() {
        Map userInfoMap = getFrontUserInfo();
        if (ObjectUtils.isEmpty(userInfoMap)) {
            return null;
        }
        return (Integer) userInfoMap.get("id");
    }
}
