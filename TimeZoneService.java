package com.evertracker.coronatower.api.timezones.services;

import com.evertracker.coronatower.api.timezones.configs.TimeZoneConfiguration;
import com.evertracker.coronatower.common.addresses.entities.GeoPoint;
import com.evertracker.coronatower.common.addresses.entities.GpsLocation;
import com.evertracker.coronatower.common.utils.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
public class TimeZoneService {
  private final WebClient webClient;

  public TimeZoneService(@Qualifier(TimeZoneConfiguration.TIME_ZONE_CLIENT) WebClient webClient) {
    this.webClient = webClient;
  }

  public Mono<ZoneId> getZoneId(GeoPoint location) {
    return getZoneId(location.coordinates[1], location.coordinates[0])
        .next()
        .map(ZoneId::of)
        .doOnError(it -> log.error("getZoneId", it));
  }

  public Mono<ZoneId> getZoneId(GpsLocation location) {
    return getZoneId(location.lat, location.lng)
        .next()
        .map(ZoneId::of)
        .doOnError(it -> log.error("getZoneId", it));
  }

  private Flux<String> getZoneId(double latitude, double longitude) {
    return webClient
        .get()
        .uri(it -> it.queryParam("latitude", latitude).queryParam("longitude", longitude).build())
        .retrieve()
        .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
        .flatMapMany(Flux::fromIterable);
  }

  public Mono<ZonedDateTime> normalizeToLocationZoneId(ZonedDateTime date, GeoPoint location) {
    return getZoneId(location)
        .map(
            zoneId -> {
              val localDateTime = LocalDateTime.ofInstant(date.timestamp, ZoneId.of(date.zoneId));
              val zonedDateTime = java.time.ZonedDateTime.of(localDateTime, zoneId);

              return new ZonedDateTime(
                  zonedDateTime.toInstant(),
                  zoneId.getId(),
                  zonedDateTime.getOffset().getTotalSeconds());
            });
  }
}
