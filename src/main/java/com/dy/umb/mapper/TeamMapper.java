package com.dy.umb.mapper;

import com.dy.umb.model.domain.Team;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;


/**
* @author dysjs
* @description 针对表【team(队伍)】的数据库操作Mapper
* @createDate 2025-03-03 15:08:02
* @Entity generator.domain.Team
*/
public interface TeamMapper extends BaseMapper<Team> {
    /**
     * 使用行级锁查询用户创建的队伍数量
     * @param userId 用户 ID
     * @return 用户创建的队伍数量
     */
    @Select("SELECT COUNT(*) FROM team WHERE userId = #{userId} FOR UPDATE")
    long countUserTeamsForUpdate(@Param("userId") Long userId);
}




