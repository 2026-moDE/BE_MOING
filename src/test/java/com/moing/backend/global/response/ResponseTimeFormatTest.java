package com.moing.backend.global.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moing.backend.BackendApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응답 DTO 시각 포맷 일관성 테스트
 *
 * <p>애너테이션이 없으면 Jackson 기본 직렬화로 타임존 표기 없이 나가, 같은 앱 안에
 * 포맷이 두 가지가 된다. 실제로 혼잡도·장소 응답에서 한 번 어긋났던 적이 있어
 * DTO를 통째로 훑어 막는다.
 */
class ResponseTimeFormatTest {

    private static final String EXPECTED_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    private static final String EXPECTED_TIMEZONE = "UTC";

    @Test
    @DisplayName("모든 응답 DTO의 LocalDateTime 필드가 같은 UTC 포맷을 쓴다")
    void 시각_포맷이_전부_같다() throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> checked = new ArrayList<>();

        for (Class<?> dto : dtoClasses()) {
            if (!dto.isRecord()) continue;

            for (RecordComponent component : dto.getRecordComponents()) {
                if (component.getType() != LocalDateTime.class) continue;

                String where = dto.getSimpleName() + "." + component.getName();
                checked.add(where);

                JsonFormat format = findJsonFormat(dto, component);
                if (format == null) {
                    violations.add(where + " — @JsonFormat 없음");
                } else if (!EXPECTED_PATTERN.equals(format.pattern())
                        || !EXPECTED_TIMEZONE.equals(format.timezone())) {
                    violations.add(where + " — pattern=" + format.pattern()
                            + ", timezone=" + format.timezone());
                }
            }
        }

        // 스캔이 아무것도 못 찾으면 테스트가 조용히 통과해 버린다
        assertThat(checked).as("검사한 LocalDateTime 필드").hasSizeGreaterThan(15);
        assertThat(violations).as("시각 포맷이 어긋난 필드").isEmpty();
    }

    /**
     * 레코드 컴포넌트에 붙인 @JsonFormat을 찾는다.
     *
     * <p>@JsonFormat의 @Target에 RECORD_COMPONENT가 없어서 컴포넌트에는 남지 않고
     * 필드와 접근자 메서드로만 전파된다. 컴포넌트만 보면 전부 "없음"으로 읽힌다.
     */
    private JsonFormat findJsonFormat(Class<?> dto, RecordComponent component) {
        JsonFormat onComponent = component.getAnnotation(JsonFormat.class);
        if (onComponent != null) return onComponent;

        JsonFormat onAccessor = component.getAccessor().getAnnotation(JsonFormat.class);
        if (onAccessor != null) return onAccessor;

        try {
            return dto.getDeclaredField(component.getName()).getAnnotation(JsonFormat.class);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    /** 각 도메인 dto 패키지의 모든 클래스. 중첩 레코드도 별도 class 파일이라 함께 걸린다. */
    private List<Class<?>> dtoClasses() throws IOException {
        Path classesRoot = classesRoot();

        try (Stream<Path> files = Files.walk(classesRoot)) {
            return files
                    .filter(path -> path.toString().endsWith(".class"))
                    .map(path -> classesRoot.relativize(path).toString()
                            .replace(".class", "")
                            .replace('/', '.'))
                    .filter(name -> name.startsWith("com.moing.backend.domain."))
                    .filter(name -> name.contains(".dto."))
                    .map(this::load)
                    .toList();
        }
    }

    private Path classesRoot() {
        Path root = Path.of(BackendApplication.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath());
        assertThat(root).as("컴파일된 클래스 경로").isDirectory();
        return root;
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(name, e);
        }
    }
}
