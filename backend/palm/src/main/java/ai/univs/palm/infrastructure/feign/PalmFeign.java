package ai.univs.palm.infrastructure.feign;

import ai.univs.palm.infrastructure.feign.dto.*;
import ai.univs.palm.shared.feign.CommonFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
        name = "palm-server",
        configuration = { CommonFeignConfig.class },
        url = "${palm.module.url}"
)
public interface PalmFeign {

    // Watchlist(branch) 생성
    @PostMapping(value = "/api/v1/Watchlists", consumes = MediaType.APPLICATION_JSON_VALUE)
    RegisterBranchFeignResponseDTO registerBranch(@RequestBody RegisterBranchFeignRequestDTO request);

    // WatchlistMember(팜) 등록
    @PostMapping(value = "/api/v1/WatchlistMembers/Register", consumes = MediaType.APPLICATION_JSON_VALUE)
    RegisterFeignResponseDTO register(@RequestBody RegisterFeignRequestDTO request);

    // WatchlistMember(팜) 삭제 — 204 No Content
    @DeleteMapping(value = "/api/v1/WatchlistMembers/{palmId}")
    void delete(@PathVariable String palmId);

    // 팜 라이브니스
    @PostMapping(value = "/api/v1/Palms/SpoofCheck", consumes = MediaType.APPLICATION_JSON_VALUE)
    LivenessFeignResponseDTO liveness(@RequestBody LivenessFeignRequestDTO request);

    // 팜 매칭 (1:N, watchlistIds 기반 검색) — 응답은 매칭 결과 배열
    @PostMapping(value = "/api/v1/Watchlists/SearchByPalm", consumes = MediaType.APPLICATION_JSON_VALUE)
    List<IdentifyFeignResponseDTO> identify(@RequestBody IdentifyFeignRequestDTO request);
}
