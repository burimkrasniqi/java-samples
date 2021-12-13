package com.evertracker.coronatower.api.deviations.dtos;

import com.evertracker.coronatower.common.deviations.deviations.DeviationType;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.util.List;

@ToString
public class DeviationOverviewResDto {
  public int deviationsCount;
  public long totalMatchesCount;

  public List<Deviation> deviations;

  @ToString
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Deviation {
    public DeviationType deviationType;
    public int count;
  }
}
