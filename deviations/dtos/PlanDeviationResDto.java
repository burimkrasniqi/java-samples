package com.evertracker.coronatower.api.deviations.dtos;

import com.evertracker.coronatower.common.utils.ZonedDateTime;
import lombok.ToString;

import java.util.List;

@ToString
public class PlanDeviationResDto {
  public static final String PLAN_ID = "planId";
  public static final String PLAN_VERSION_ID = "planVersionId";
  public static final String PLAN_NAME = "planName";
  public static final String DEPART_AT = "departAt";
  public static final String ARRIVE_AT = "arriveAt";
  public static final String POINTS_OF_DEVIATION = "pointsOfDeviations";
  public static final String EARLY_COUNT = "earlyCount";
  public static final String LATE_COUNT = "lateCount";

  public String planId;
  public String planVersionId;
  public String planName;
  public ZonedDateTime departAt;
  public ZonedDateTime arriveAt;
  public List<String> pointsOfDeviations;

  public int earlyCount;
  public int lateCount;
}
