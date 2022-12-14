package io.metersphere.base.mapper.ext;

import io.metersphere.base.domain.TestCaseReviewApiCase;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ExtTestCaseReviewApiCaseMapper {
     void  insertIfNotExists(@Param("request")TestCaseReviewApiCase testCaseReviewApiCase);

     // todo check
//     List<TestPlanApiCaseDTO> list(@Param("request") ApiTestCaseRequest request);

     List<String> getExecResultByReviewId(String reviewId);

     List<String> getIdsByReviewId(String reviewId);

     List<String> getNotRelevanceCaseIds(@Param("reviewId")String reviewId, @Param("relevanceProjectIds")List<String> relevanceProjectIds);

     List<String> getStatusByTestReviewId(String id);
}
