package com.dy.umb.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dy.umb.aop.CacheDeleteHelper;
import com.dy.umb.aop.CacheDoubleDelete;
import com.dy.umb.aop.CacheGetList;
import com.dy.umb.aop.CachePage;
import com.dy.umb.common.BaseResponse;
import com.dy.umb.model.request.DeleteRequest;
import com.dy.umb.common.ErrorCode;
import com.dy.umb.common.ResultUtils;
import com.dy.umb.exception.BusinessException;
import com.dy.umb.model.domain.Team;
import com.dy.umb.model.domain.User;
import com.dy.umb.model.domain.UserTeam;
import com.dy.umb.model.dto.TeamQuery;
import com.dy.umb.model.request.TeamAddRequest;
import com.dy.umb.model.request.TeamJoinRequest;
import com.dy.umb.model.request.TeamQuitRequest;
import com.dy.umb.model.request.TeamUpdateRequest;
import com.dy.umb.model.vo.TeamUserVO;
import com.dy.umb.service.TeamService;
import com.dy.umb.service.UserService;
import com.dy.umb.service.UserTeamService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/team")
@Slf4j
public class TeamController {

    @Resource
    private UserService userService;

    @Resource
    private TeamService teamService;

    @Resource
    private UserTeamService userTeamService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private CacheDeleteHelper cacheDeleteHelper;

    @PostMapping("/add")
    public BaseResponse<Long> addTeam(@RequestBody TeamAddRequest teamAddRequest, HttpServletRequest request) {
        if (teamAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        Team team = new Team();
        BeanUtils.copyProperties(teamAddRequest, team);
        long teamId = teamService.addTeam(team, loginUser);

        // 插入成功后再延迟双删
        String key = "team:info:" + teamId;
        cacheDeleteHelper.doubleDelete(key);

        return ResultUtils.success(teamId);
    }


    @CacheDoubleDelete(keys = {"team:info:{#teamUpdateRequest.id}"})
    @PostMapping("/update")
    public BaseResponse<Boolean> updateTeam(@RequestBody TeamUpdateRequest teamUpdateRequest, HttpServletRequest request) {
        if (teamUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.updateTeam(teamUpdateRequest, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "更新失败");
        }

        return ResultUtils.success(true);
    }

    @GetMapping("/get")
    public BaseResponse<Team> getTeamById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // redis缓存
        String key = "team:info:" + id;
        Team team = (Team) redisTemplate.opsForValue().get(key);

        if (team == null) {
            String lockKey = "lock:team:" + id;
            RLock lock = redissonClient.getLock(lockKey);

            // 尝试获取分布式锁
            try {
                // 尝试获取锁，设置锁的过期时间（防止死锁）
                boolean isLocked = lock.tryLock(10, 30, TimeUnit.SECONDS);

                if (isLocked) {
                    try {
                        // 双重检查，防止其他线程已经重建了缓存
                        team = (Team) redisTemplate.opsForValue().get(key);
                        if (team != null) {
                            redisTemplate.opsForValue().set(key, team, 30, TimeUnit.MINUTES);
                            return ResultUtils.success(team);
                        }

                        // 3. 查询数据库
                        team = teamService.getById(id);

                        if (team != null) {
                            // 4. 重新设置缓存，设置合理的过期时间
                            redisTemplate.opsForValue().set(key, team, 30, TimeUnit.MINUTES);
                        } else {
                            // 5. 如果数据库也没有，缓存一个空值，防止缓存穿透
                            redisTemplate.opsForValue().set(key, null, 5, TimeUnit.MINUTES);
                        }

                        return ResultUtils.success(team);
                    } finally {
                        // 6. 释放锁
                        lock.unlock();
                    }
                } else {
                    // 7. 如果获取锁失败，稍等后重试或返回默认值
                    Thread.sleep(100); // 简单等待
                    return getTeamById(id); // 递归重试
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Failed to acquire lock", e);
            }
        }

        return ResultUtils.success(team);
    }

    // 管理员获取所有队伍列表
    @GetMapping("/list")
    public BaseResponse<List<TeamUserVO>> listTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        boolean isAdmin = userService.isAdmin(request);
        // 1. 查询队伍列表
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, isAdmin);
        final List<Long> teamIdList = teamList.stream().map(TeamUserVO::getId).collect(Collectors.toList());

        try {
            User loginUser = userService.getLoginUser(request);
            Long userId = loginUser.getId();
            String cacheKey = "user:team:joined:" + userId;

            // 2. 从Redis中获取值
            Object value = redisTemplate.opsForValue().get(cacheKey);
            Set<Long> hasJoinTeamIdSet = null;
            // 检查获取的值是否为Set类型
            if (value instanceof Set) {
                Set<?> set = (Set<?>) value;
                // 检查Set中的元素是否为Long类型
                boolean allLong = true;
                for (Object element : set) {
                    if (!(element instanceof Long)) {
                        allLong = false;
                        break;
                    }
                }
                if (allLong) {
                    // 安全地转换为Set<Long>
                    hasJoinTeamIdSet = (Set<Long>) set;
                }
            }

            if (hasJoinTeamIdSet == null) {
                // 缓存未命中，查数据库
                QueryWrapper<UserTeam> userTeamQueryWrapper = new QueryWrapper<>();
                userTeamQueryWrapper.eq("userId", userId);
                List<UserTeam> userTeamList = userTeamService.list(userTeamQueryWrapper);
                hasJoinTeamIdSet = userTeamList.stream()
                        .map(UserTeam::getTeamId)
                        .collect(Collectors.toSet());

                // 设置缓存，过期时间加点随机避免雪崩
                redisTemplate.opsForValue().set(
                        cacheKey,
                        hasJoinTeamIdSet,
                        Duration.ofMinutes(10).plusSeconds(new Random().nextInt(300))
                );
            }

            // 3. 标记是否加入
            Set<Long> finalHasJoinTeamIdSet = hasJoinTeamIdSet;
            teamList.forEach(team -> {
                boolean hasJoin = finalHasJoinTeamIdSet.contains(team.getId());
                team.setHasJoin(hasJoin);
            });

        } catch (Exception e) {
            // 兜底处理，不影响主流程
        }

        // 4. 查询已加入人数（这段可选缓存，暂不优化）
        QueryWrapper<UserTeam> userTeamJoinQueryWrapper = new QueryWrapper<>();
        userTeamJoinQueryWrapper.in("teamId", teamIdList);
        List<UserTeam> userTeamList = userTeamService.list(userTeamJoinQueryWrapper);
        Map<Long, List<UserTeam>> teamIdUserTeamList = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));

        teamList.forEach(team -> {
            int joinNum = teamIdUserTeamList.getOrDefault(team.getId(), new ArrayList<>()).size();
            team.setHasJoinNum(joinNum);
        });

        return ResultUtils.success(teamList);
    }


    @CachePage(keyPrefix = "team:list:page")
    @GetMapping("/list/page")
    public BaseResponse<Page<Team>> listTeamsByPage(TeamQuery teamQuery) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Team team = new Team();
        BeanUtils.copyProperties(teamQuery, team);
        Page<Team> page = new Page<>(teamQuery.getPageNum(), teamQuery.getPageSize());
        QueryWrapper<Team> queryWrapper = new QueryWrapper<>(team);
        Page<Team> resultPage = teamService.page(page, queryWrapper);
        return ResultUtils.success(resultPage);
    }


    @CacheDoubleDelete(keys = {"team:info:{#teamJoinRequest.teamId}"})
    @PostMapping("/join")
    public BaseResponse<Boolean> joinTeam(@RequestBody TeamJoinRequest teamJoinRequest, HttpServletRequest request) {
        if (teamJoinRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.joinTeam(teamJoinRequest, loginUser);
        return ResultUtils.success(result);
    }

    @CacheDoubleDelete(keys = {"team:info:{#teamQuitRequest.teamId}"})
    @PostMapping("/quit")
    public BaseResponse<Boolean> quitTeam(@RequestBody TeamQuitRequest teamQuitRequest, HttpServletRequest request) {
        if (teamQuitRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.quitTeam(teamQuitRequest, loginUser);
        return ResultUtils.success(result);
    }

    @CacheDoubleDelete(keys = {"team:info:{#deleteRequest.id}"})
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteTeam(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        User loginUser = userService.getLoginUser(request);
        boolean result = teamService.deleteTeam(id, loginUser);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "删除失败");
        }
        return ResultUtils.success(true);
    }


    /**
     * 获取我创建的队伍
     *
     * @param teamQuery
     * @param request
     * @return
     */
    @CacheGetList(keyPrefix = "team:list:my:create:")
    @GetMapping("/list/my/create")
    public BaseResponse<List<TeamUserVO>> listMyCreateTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        teamQuery.setUserId(loginUser.getId());
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        return ResultUtils.success(teamList);
    }


    /**
     * 获取我加入的队伍
     *
     * @param teamQuery
     * @param request
     * @return
     */
    @CacheGetList(keyPrefix = "team:list:my:join:")
    @GetMapping("/list/my/join")
    public BaseResponse<List<TeamUserVO>> listMyJoinTeams(TeamQuery teamQuery, HttpServletRequest request) {
        if (teamQuery == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        QueryWrapper<UserTeam> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", loginUser.getId());
        List<UserTeam> userTeamList = userTeamService.list(queryWrapper);

        Map<Long, List<UserTeam>> listMap = userTeamList.stream()
                .collect(Collectors.groupingBy(UserTeam::getTeamId));
        List<Long> idList = new ArrayList<>(listMap.keySet());
        teamQuery.setIdList(idList);
        List<TeamUserVO> teamList = teamService.listTeams(teamQuery, true);
        return ResultUtils.success(teamList);
    }
}
