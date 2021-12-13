package com.evertracker.coronatower.api.deviations.dtos;

import lombok.ToString;

@ToString
public class PlanDeviationSegmentResDto {
  public static final String ID = "id";
  public static final String MIN = "minInSeconds";
  public static final String MAX = "maxInSeconds";
  public static final String AVG = "avgInSeconds";
  public static final String MEDIAN = "medianInSeconds";
  public static final String SAME_LOCATION = "sameLocation";
  public static final String FROM = "from";
  public static final String TO = "to";

  public String id;
  public String from;
  public String to;
  public Boolean sameLocation;
  public Long planetFromInSeconds;
  public Long planetToInSeconds;
  public Long minInSeconds;
  public Long maxInSeconds;
  public Long avgInSeconds;
  public Long medianInSeconds;
}
