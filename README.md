# GongmoaView MCP (공모아뷰 MCP)

[공모아뷰](https://www.gongmoaview.com)는 공모전·대외활동·교육·강연 공고를 여러 사이트에서 모아 한곳에서 비교하는 서비스입니다.
이 저장소는 공모아뷰가 제공하는 **읽기 전용 원격 MCP 서버**의 공개 안내와 참고용 소스입니다.

GongmoaView aggregates Korean contests, extracurricular activities and education listings from many sites into one comparable list.
This repository documents its **read-only remote MCP server**.

## 연결 / Connect

| 항목 | 값 |
|---|---|
| 주소 (Endpoint) | `https://www.gongmoaview.com/mcp` |
| 전송 방식 (Transport) | Streamable HTTP (상태 없음, stateless) |
| 인증 (Auth) | 없음 (none) |
| 서버 카드 | <https://www.gongmoaview.com/.well-known/mcp/server-card.json> |
| 코드표 문서 | <https://www.gongmoaview.com/llms.txt> |

Claude: 설정 → 커넥터 → 커스텀 커넥터 추가 → 주소 입력, 인증 없음.

## 도구 / Tools

모든 도구는 읽기 전용입니다 (`readOnlyHint: true`, `destructiveHint: false`, `openWorldHint: false`). 한 번에 최대 20건을 돌려줍니다.
전체 입력 스키마는 [`tools.json`](tools.json)에 있습니다.

- **`search_contests`** — 접수 중인 공고를 조건(검색어, 유형, 분야, 대상, 마감 시기, 지역, 정렬, 오늘/어제 새 공고)으로 찾습니다.
- **`get_contest`** — 공고 한 건의 접수 기간·대상·상금·주최와 올라온 사이트를 읽습니다.

결과에는 항상 공모아뷰 공고 페이지 링크가 들어 있습니다. 지원과 접수는 각 공고의 주최 측 원문에서 해야 하며, 공모아뷰는 공고를 직접 주최하거나 접수하지 않습니다.

## 개인정보 / Privacy

로그인이 없고 개인정보를 요청하거나 저장하지 않습니다. 공개 화면과 같은 공고 정보만 돌려줍니다.
[개인정보처리방침](https://www.gongmoaview.com/privacy) · [이용약관](https://www.gongmoaview.com/terms) · [문의](https://www.gongmoaview.com/about)

## 이 저장소의 파일

- [`tools.json`](tools.json) — 서버가 `tools/list`로 알려 주는 도구 정의 (실제 응답)
- [`server-card.json`](server-card.json) — 서버 카드 (실제 응답)
- [`McpController.java`](McpController.java) — 서버의 MCP 처리부 (Spring Boot). 공모아뷰 내부의 `Catalog`·`Codes`·`Areas`·`Geo` 등에 의존하므로 **이 파일만으로는 빌드되지 않는 참고용**입니다.

## 라이선스

따로 정하지 않았습니다. 사용·재배포 조건은 저장소 소유자에게 문의하세요.
