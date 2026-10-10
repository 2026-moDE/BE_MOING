package com.moing.backend.domain.congestion.service;

import com.moing.backend.domain.congestion.dto.CongestionResponse;
import com.moing.backend.domain.congestion.entity.SeoulArea;
import com.moing.backend.domain.congestion.repository.SeoulAreaRepository;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import com.moing.backend.global.infra.SeoulCityDataResponse;
import com.moing.backend.global.infra.SeoulPublicDataService;
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
     * 좌표에서 가장 가까운 서울시 도시데이터 지역의 실시간 혼잡도와 12시간 예측을 조회한다.
     *
     * @throws CustomException 반경 내 지역이 없으면 404, 서울시 응답이 없으면 502
     */
    public CongestionResponse getNearbyCongestion(double latitude, double longitude) {
        SeoulArea area = findNearestArea(latitude, longitude);

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
                toForecast(row.fcstPpltn()));
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
