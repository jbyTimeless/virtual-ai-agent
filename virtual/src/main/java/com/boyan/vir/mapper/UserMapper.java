package com.boyan.vir.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.boyan.vir.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper —— MyBatis-Plus BaseMapper
 * 自动拥有 selectById, insert, updateById, selectOne 等方法
 */
@Mapper
public interface UserMapper extends BaseMapper<SysUser> {
}
