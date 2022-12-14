package io.metersphere.service;


import io.metersphere.base.domain.RelationshipEdge;
import io.metersphere.base.domain.RelationshipEdgeExample;
import io.metersphere.base.domain.RelationshipEdgeKey;
import io.metersphere.base.mapper.RelationshipEdgeMapper;
import io.metersphere.base.mapper.ext.BaseRelationshipEdgeMapper;
import io.metersphere.commons.exception.MSException;
import io.metersphere.commons.utils.SessionUtils;
import io.metersphere.request.RelationshipEdgeRequest;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author jianxingChen
 */

@Service
@Transactional(rollbackFor = Exception.class)
public class RelationshipEdgeService {

    @Resource
    private RelationshipEdgeMapper relationshipEdgeMapper;
    @Resource
    private BaseRelationshipEdgeMapper baseRelationshipEdgeMapper;
    @Resource
    private SqlSessionFactory sqlSessionFactory;

    public void delete(String sourceId, String targetId) {
        RelationshipEdgeExample example = new RelationshipEdgeExample();
        example.createCriteria()
                .andSourceIdEqualTo(sourceId)
                .andTargetIdEqualTo(targetId);
        List<RelationshipEdge> list = relationshipEdgeMapper.selectByExample(example);
        if (CollectionUtils.isNotEmpty(list)) {
            String graphId = relationshipEdgeMapper.selectByExample(example).get(0).getGraphId();
            updateGraphId(graphId, sourceId, targetId);
            relationshipEdgeMapper.deleteByExample(example);
        }
    }

    public void delete(String sourceId ,List<String> targetIds) {
        targetIds.forEach(targetId -> {
            delete(sourceId, targetId);
        });
    }

    /**
     * ????????????????????????????????????????????????????????????
     * @param graphId
     * @param sourceId
     * @param targetId
     */
    public void updateGraphId(String graphId, String sourceId, String targetId) {
        RelationshipEdgeExample graphExample = new RelationshipEdgeExample();
        graphExample.createCriteria()
                .andGraphIdEqualTo(graphId);
        List<RelationshipEdge> edges = relationshipEdgeMapper.selectByExample(graphExample);

        // ?????????????????????
        edges = edges.stream()
                .filter(i -> !i.getSourceId().equals(sourceId) && !i.getTargetId().equals(targetId))
                .collect(Collectors.toList());

        Set<String> nodes = new HashSet<>();
        Set<String> markSet = new HashSet<>();
        nodes.addAll(edges.stream().map(RelationshipEdgeKey::getSourceId).collect(Collectors.toSet()));
        nodes.addAll(edges.stream().map(RelationshipEdgeKey::getTargetId).collect(Collectors.toSet()));

        dfsForMark(sourceId, edges, markSet, true);
        dfsForMark(sourceId, edges, markSet, false);

        // ??????????????????????????????????????????????????????????????????????????????graphId
        if (markSet.size() != nodes.size()) {
            List<String> updateIds = new ArrayList<>(markSet);
            RelationshipEdgeExample updateGraphExample = new RelationshipEdgeExample();
            updateGraphExample.createCriteria()
                    .andSourceIdIn(updateIds);
            updateGraphExample.or(
                    updateGraphExample.createCriteria().andTargetIdIn(updateIds)
            );
            RelationshipEdge edge = new RelationshipEdge();
            edge.setGraphId(UUID.randomUUID().toString());
            relationshipEdgeMapper.updateByExampleSelective(edge, updateGraphExample);
        }
    }

    /**
     * ???????????????????????????
     * @param node
     * @param edges
     * @param markSet
     * @param isForwardDirection
     */
    public void dfsForMark(String node, List<RelationshipEdge> edges, Set<String> markSet, boolean isForwardDirection) {
        markSet.add(node);

        Set<String> nextLevelNodes = new HashSet<>();

        for (RelationshipEdge edge : edges) {
            if (isForwardDirection) {
                if (node.equals(edge.getSourceId())) {
                    nextLevelNodes.add(edge.getTargetId());
                }
            } else {
                if (node.equals(edge.getTargetId())) {
                    nextLevelNodes.add(edge.getSourceId());
                }
            }
        }

        nextLevelNodes.forEach(nextNode -> {
            if (!markSet.contains(nextNode)) {
                dfsForMark(nextNode, edges, markSet, true);
                dfsForMark(nextNode, edges, markSet, false);
            }
        });
    }

    public void delete(String sourceIdOrTargetId) {
        RelationshipEdgeExample example = new RelationshipEdgeExample();
        example.createCriteria()
                .andSourceIdEqualTo(sourceIdOrTargetId);
        example.or(example.createCriteria()
                .andTargetIdEqualTo(sourceIdOrTargetId));
        relationshipEdgeMapper.deleteByExample(example);
    }

    public void delete(List<String> sourceIdOrTargetIds) {
        RelationshipEdgeExample example = new RelationshipEdgeExample();
        example.createCriteria()
                .andSourceIdIn(sourceIdOrTargetIds);
        example.or(example.createCriteria()
                .andTargetIdIn(sourceIdOrTargetIds));
        relationshipEdgeMapper.deleteByExample(example);
    }

    public List<RelationshipEdge> getRelationshipEdgeByType(String id, String relationshipType) {
        if (StringUtils.equals(relationshipType, "PRE")) {
            return getBySourceId(id);
        }  else if (StringUtils.equals(relationshipType, "POST")) {
            return getByTargetId(id);
        }
        return new ArrayList<>();
    }

    public List<String> getRelationIdsByType(String relationshipType, List<RelationshipEdge> relationshipEdges) {
        if (StringUtils.equals(relationshipType, "PRE")) {
            return relationshipEdges.stream()
                    .map(RelationshipEdge::getTargetId)
                    .collect(Collectors.toList());
        }  else if (StringUtils.equals(relationshipType, "POST")) {
            return relationshipEdges.stream()
                    .map(RelationshipEdge::getSourceId)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    public List<RelationshipEdge> getBySourceId(String sourceId) {
        RelationshipEdgeExample example = new RelationshipEdgeExample();
        example.createCriteria()
                .andSourceIdEqualTo(sourceId);
        return relationshipEdgeMapper.selectByExample(example);
    }

    public List<RelationshipEdge> getByTargetId(String targetId) {
        RelationshipEdgeExample example = new RelationshipEdgeExample();
        example.createCriteria()
                .andTargetIdEqualTo(targetId);
        return relationshipEdgeMapper.selectByExample(example);
    }

    public List<RelationshipEdge> getBySourceIdOrTargetId(String id) {
        RelationshipEdgeExample example = new RelationshipEdgeExample();
        example.createCriteria()
                .andSourceIdEqualTo(id);
        example.or(
                example.createCriteria()
                        .andTargetIdEqualTo(id)
        );
        return relationshipEdgeMapper.selectByExample(example);
    }

    /**
     * ???????????????
     * ?????????????????????
     * ????????????????????????????????????????????????
     * @param request
     */
    public void saveBatch(RelationshipEdgeRequest request) {

        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        RelationshipEdgeMapper batchMapper = sqlSession.getMapper(RelationshipEdgeMapper.class);

        String graphId = UUID.randomUUID().toString();
        List<RelationshipEdge> relationshipEdges = getEdgesBySaveRequest(request);
        Set<String> addEdgesIds = new HashSet<>();

        if (CollectionUtils.isNotEmpty(request.getTargetIds())) {
            request.getTargetIds().forEach(targetId -> {
                RelationshipEdge edge = getNewRelationshipEdge(graphId, request.getId(), targetId, request.getType());
                relationshipEdges.add(edge);
                addEdgesIds.add(edge.getSourceId() + edge.getTargetId());
            });
        }

        if (CollectionUtils.isNotEmpty(request.getSourceIds())) {
            request.getSourceIds().forEach(sourceId -> {
                RelationshipEdge edge = getNewRelationshipEdge(graphId, sourceId, request.getId(), request.getType());
                relationshipEdges.add(edge);
                addEdgesIds.add(edge.getSourceId() + edge.getTargetId());
            });
        }

        HashSet<String> nodeIds = new HashSet<>();
        nodeIds.addAll(relationshipEdges.stream().map(RelationshipEdge::getSourceId).collect(Collectors.toSet()));
        nodeIds.addAll(relationshipEdges.stream().map(RelationshipEdge::getTargetId).collect(Collectors.toSet()));
        // ??????????????????
        HashSet<String> visitedSet = new HashSet<>();
        nodeIds.forEach(nodeId -> {
            if (!visitedSet.contains(nodeId) && directedCycle(nodeId, relationshipEdges, new HashSet<>(), visitedSet)) {
                MSException.throwException("???????????????????????????????????????????????????");
            };
        });

        relationshipEdges.forEach(item -> {
            if (addEdgesIds.contains(item.getSourceId() + item.getTargetId())) {
                if(batchMapper.selectByPrimaryKey(item) == null ) {
                    batchMapper.insert(item);
                }else{
                    item.setGraphId(graphId); // ???????????????id??????????????????????????????id
                    batchMapper.updateByPrimaryKey(item);
                }
            } else {
                item.setGraphId(graphId); // ???????????????id??????????????????????????????id
                batchMapper.updateByPrimaryKey(item);
            }
        });
        sqlSession.flushStatements();
        if (sqlSession != null && sqlSessionFactory != null) {
            SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
        }
    }

    private RelationshipEdge getNewRelationshipEdge(String graphId, String sourceId, String targetId, String type) {
        RelationshipEdge edge = new  RelationshipEdge();
        edge.setCreator(SessionUtils.getUserId());
        edge.setGraphId(graphId);
        edge.setCreateTime(System.currentTimeMillis());
        edge.setSourceId(sourceId);
        edge.setTargetId(targetId);
        edge.setType(type);
        return edge;
    }

    /**
     * ?????????????????????????????????????????????
     * @param request
     * @return
     */
    public List<RelationshipEdge>  getEdgesBySaveRequest(RelationshipEdgeRequest request) {
        List<String> graphNodes = new ArrayList<>();
        graphNodes.add(request.getId());
        if (request.getTargetIds() != null) {
            graphNodes.addAll(request.getTargetIds());
        }
        if (request.getSourceIds() != null) {
            graphNodes.addAll(request.getSourceIds());
        }

        List<String> graphIds = baseRelationshipEdgeMapper.getGraphIdsByNodeIds(graphNodes);
        if (CollectionUtils.isEmpty(graphIds)) {
            return new ArrayList<>();
        }
        RelationshipEdgeExample example = new RelationshipEdgeExample();
        example.createCriteria()
                .andGraphIdIn(graphIds);

        return relationshipEdgeMapper.selectByExample(example);
    }

    /**
     * ?????????????????????????????????????????????????????????
     * @param id
     * @param edges
     * @param markSet     ?????????????????????????????????
     * @param visitedSet  ????????????????????????
     * @return
     */
    public boolean directedCycle(String id, List<RelationshipEdge> edges, Set<String> markSet, Set<String> visitedSet) {

        if (markSet.contains(id)) {
            // ???????????????????????????????????????????????????
            return true;
        }

        markSet.add(id);
        visitedSet.add(id);

        ArrayList<String> nextLevelNodes = new ArrayList();
        for (RelationshipEdge relationshipEdge : edges) {
            if (id.equals(relationshipEdge.getSourceId())) {
                nextLevelNodes.add(relationshipEdge.getTargetId());
            }
        }

        for (String nextNode : nextLevelNodes) {
            if (directedCycle(nextNode, edges, markSet, visitedSet)) {
                return true;
            }
        }

        // ????????????????????????????????????????????????????????????????????????????????????
        // ?????? 1->3, 1->2->3 , 3 ????????????????????????
        markSet.remove(id);

        return false;
    }

    /**
     * ????????????????????????????????????????????????id
     * @param nodeId
     * @return
     */
    public List<String> getRelationshipIds(String nodeId) {
        List<RelationshipEdge> sourceRelationshipEdges = getBySourceIdOrTargetId(nodeId);
        List<String> ids = sourceRelationshipEdges.stream().map(RelationshipEdge::getTargetId).collect(Collectors.toList());
        ids.addAll(sourceRelationshipEdges.stream().map(RelationshipEdge::getSourceId).collect(Collectors.toList()));
        ids.add(nodeId);
        return ids;
    }

    public int getRelationshipCount(String id, Function<List<String>, Integer> countByIdsFunc) {
        List<String> ids = getRelationshipIds(id);
        ids = ids.stream().filter(i -> !i.equals(id)).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(ids)) {
            return countByIdsFunc.apply(ids);
        }
        return 0;
    }
}
