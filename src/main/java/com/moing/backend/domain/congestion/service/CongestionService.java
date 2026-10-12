package com.moing.backend.domain.congestion.service;

import com.moing.backend.domain.congestion.dto.CongestionResponse;
import com.moing.backend.domain.congestion.dto.SeoulAreaSearchResponse;
import com.moing.backend.domain.congestion.entity.SeoulArea;
import com.moing.backend.domain.congestion.repository.SeoulAreaRepository;
import com.moing.backend.domain.follow.util.ChosungUtil;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.global.infra.SeoulCityDataResponse;
import com.moing.backend.global.infra.SeoulPublicDataService;
import com.moing.backend.global.infra.SeoulWeatherResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CongestionService {

    /**
     * 지역 매칭 반경(m).
     *
     * <p>서울시 도시데이터는 120여 곳의 넓은 지역 단위라 기준 장소와 1:1로 대응하지 않는다.
     * 좌표가 지역 중심에서 2km 넘게 떨어졌다면 그 지역의 혼잡도를 그 장소의 상황이라고
     * 보기 어려우므로 404로 끊는다.
     */
    private static final int AREA_MATCH_RADIUS = 2_000;

    /** 혼잡도 추이 띠가 12시간치라 그 이상은 쓰지 않는다 (서울시 응답은 1시간 간격 12건) */
    private static final int FORECAST_LIMIT = 12;

    private final SeoulAreaRepository seoulAreaRepository;
    private final SeoulPublicDataService seoulPublicDataService;

    /**
     * 좌표에서 가장 가까운 서울시 도시데이터 지역의 실시간 혼잡도·12시간 예측·날씨를 조회한다.
     *
     * <p>날씨는 혼잡도의 부가 정보다. 서울시 날씨 응답이 비거나 실패하면 그 필드만 null로
     * 두고 혼잡도는 그대로 내려준다 — 날씨 때문에 바텀시트 전체가 못 뜨면 손해가 더 크다.
     *
     * @throws CustomException 반경 내 지역이 없으면 404, 서울시 인구 응답이 없으면 502
     */
    public CongestionResponse getNearbyCongestion(double latitude, double longitude) {
        return getCongestion(findNearestArea(latitude, longitude));
    }

    /**
     * 사용자가 직접 고른 지역의 실시간 혼잡도·12시간 예측·날씨를 조회한다.
     *
     * <p>좌표가 아니라 id로 받는다. 지역 121곳 중 314쌍이 서로 2km 안에 있고 가장 가까운
     * 쌍은 74m밖에 안 떨어져 있어(동대문 관광특구 ↔ DDP), 좌표를 넘겨 최근접으로 다시
     * 찾게 하면 고른 지역이 아닌 옆 지역이 잡힐 수 있다. 고른 지역은 추론할 대상이 아니다.
     *
     * @throws CustomException 없는 지역이면 404, 서울시 인구 응답이 없으면 502
     */
    public CongestionResponse getAreaCongestion(Long areaId) {
        SeoulArea area = seoulAreaRepository.findById(areaId)
                .orElseThrow(() -> new CustomException(ErrorCode.AREA_NOT_FOUND));
        return getCongestion(area);
    }

    private CongestionResponse getCongestion(SeoulArea area) {
        SeoulCityDataResponse.Row row = seoulPublicDataService.fetchPopulation(area.getAreaNm());
        if (row == null) {
            throw new CustomException(ErrorCode.EXTERNAL_API_ERROR);
        }

        return new CongestionResponse(
                area.getAreaNm(),
                mapPublicCongestion(row.areaCongestLvl()),
                row.areaCongestMsg(),
                row.areaPpltnMin(),
                row.areaPpltnMax(),
                row.ppltnTime(),
                toForecast(row.fcstPpltn()),
                toWeather(seoulPublicDataService.fetchWeather(area.getAreaNm())));
    }

    /**
     * 서울시 날씨 구역을 응답 DTO로 옮긴다.
     *
     * <p>모든 값이 문자열로 오고 값이 없으면 {@code "-"}가 들어오므로 전부 숫자 변환을 거친다.
     * 하늘 상태·강수 확률은 현재값이 없어 다음 정시 예보에서 가져온다.
     */
    private CongestionResponse.Weather toWeather(SeoulWeatherResponse.Weather weather) {
        if (weather == null) return null;

        SeoulWeatherResponse.HourlyForecast nextHour = weather.nextHour();

        return new CongestionResponse.Weather(
                toDouble(weather.temp()),
                toDouble(weather.maxTemp()),
                toDouble(weather.minTemp()),
                toInt(weather.humidity()),
                toDouble(weather.precipitation()),
                weather.precptType(),
                weather.pcpMsg(),
                nextHour != null ? nextHour.skyStts() : null,
                nextHour != null ? toInt(nextHour.rainChance()) : null,
                toInt(weather.pm10()),
                weather.pm10Index(),
                toInt(weather.pm25()),
                weather.pm25Index(),
                weather.airIdx(),
                weather.airMsg(),
                toInt(weather.uvIndex()),
                weather.uvIndexLvl(),
                weather.uvMsg(),
                weather.sunrise(),
                weather.sunset(),
                weather.weatherTime());
    }

    /** 서울시는 값이 없을 때 "-"를 쓴다. 그 밖의 비숫자도 같이 걸러 null로 둔다 */
    private Double toDouble(String value) {
        try {
            return value == null ? null : Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toInt(String value) {
        Double parsed = toDouble(value);
        return parsed == null ? null : (int) Math.round(parsed);
    }

    /**
     * 사용자가 고를 수 있는 지역을 검색한다.
     *
     * <p>선택 대상은 서울시가 실시간 인구를 내려주는 지역뿐이므로 {@code seoul_areas}만 본다.
     * 여기 없는 곳을 고르면 혼잡도 조회가 빈손으로 끝나기 때문에 외부 검색을 섞지 않는다.
     *
     * <p>전체가 121곳뿐이라 커서·개수 제한 없이 일치하는 지역을 모두 내려준다.
     *
     * @param keyword 비어 있으면 전체 목록 (검색창 초기 화면용)
     */
    public SeoulAreaSearchResponse searchAreas(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return SeoulAreaSearchResponse.from(seoulAreaRepository.findAllByOrderByAreaNmAsc());
        }

        String trimmed = keyword.trim();

        // 초성이 섞여 있으면 부분 일치로는 잡히지 않으므로 정규식으로 찾는다 ("ㅎㄷ" → 홍대입구역)
        List<SeoulArea> areas = ChosungUtil.hasChosung(trimmed)
                ? seoulAreaRepository.findByAreaNmRegex(ChosungUtil.toRegexPattern(trimmed))
                : seoulAreaRepository.findByAreaNmContainingIgnoreCaseOrderByAreaNmAsc(trimmed);

        return SeoulAreaSearchResponse.from(areas);
    }

    private SeoulArea findNearestArea(double latitude, double longitude) {
        return seoulAreaRepository
                .findNearest(latitude, longitude, AREA_MATCH_RADIUS, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.AREA_NOT_FOUND));
    }

    /**
     * 예측은 12시간치를 전부 내려준다.
     * 혼잡도 추이 띠가 연속 그라데이션이라 중간 지점이 있어야 그릴 수 있고,
     * 현재·6시간 후·12시간 후 라벨은 프론트가 이 배열에서 골라 쓴다.
     */
    private List<CongestionResponse.ForecastItem> toForecast(List<SeoulCityDataResponse.Forecast> forecasts) {
        if (forecasts == null) return List.of();

        return forecasts.stream()
                .filter(Objects::nonNull)
                .filter(f -> f.fcstTime() != null)
                .limit(FORECAST_LIMIT)
                .map(f -> new CongestionResponse.ForecastItem(
                        f.fcstTime(),
                        mapPublicCongestion(f.fcstCongestLvl()),
                        f.fcstPpltnMin(),
                        f.fcstPpltnMax()))
                .toList();
    }

    // 서울시 공공 API 혼잡도 4단계 → CongestionLevel 1:1 매핑
    // (case 값은 서울시가 내려주는 원본 문자열이라 표시 라벨과 별개로 유지한다)
    private CongestionLevel mapPublicCongestion(String lvl) {
        if (lvl == null) return CongestionLevel.RELAXED;
        return switch (lvl) {
            case "여유" -> CongestionLevel.RELAXED;
            case "보통" -> CongestionLevel.MODERATE;
            case "약간 붐빔" -> CongestionLevel.CROWDED;
            case "붐빔" -> CongestionLevel.VERY_CROWDED;
            default -> CongestionLevel.RELAXED;
        };
    }
}
