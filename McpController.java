package com.gongmoro;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.*;

/**
 * 읽기 전용 MCP 서버 (Streamable HTTP, 상태 없음) — AI 도구(Claude·ChatGPT 등)가 공모아뷰 공고를 조건으로 바로 조회 (2026-11-17).
 * 도구 둘: search_contests · get_contest. 쓰는 기능 없음, 공개 화면과 같은 값만, 한 번에 20건까지.
 * 결과 글에는 항상 '공모아뷰'와 공모아뷰 공고 주소(/contests/…)를 넣는다 — 출처 사이트·/go/ 주소는 내보내지 않는다.
 * 요청 횟수는 RateGuard·ProbeGuard 가 다른 화면과 똑같이 센다.
 */
@RestController
public class McpController {
    static final List<String> VERSIONS = List.of("2025-06-18", "2025-03-26", "2024-11-05");
    static final int MAX = 20;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Catalog catalog;
    private final String siteUrl;

    public McpController(Catalog catalog, @Value("${gongmoro.site-url}") String siteUrl) { this.catalog = catalog; this.siteUrl = siteUrl; }

    @GetMapping("/mcp")
    ResponseEntity<Void> get() { return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header("Allow", "POST").build(); }

    @PostMapping(value = "/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> post(@RequestBody String body, HttpServletRequest req) throws Exception {
        String origin = req.getHeader("Origin");   // 브라우저 페이지가 몰래 부르는 것(DNS rebinding) 막기: Origin 이 있으면 우리 것이어야
        if (origin != null && !origin.equals(siteUrl)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        if (body.length() > 20_000) return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        JsonNode in;
        try { in = JSON.readTree(body); } catch (Exception e) { return ok(error(null, -32700, "JSON 을 읽을 수 없어요")); }
        if (in == null || !in.isObject()) return ok(error(null, -32600, "요청 하나(JSON 객체)만 받아요"));
        JsonNode id = in.get("id");
        String method = in.path("method").asText("");
        if (id == null || id.isNull()) return ResponseEntity.accepted().build();   // 알림(notifications/…) — 답이 없다
        return ok(handle(id, method, in.path("params")));
    }

    private ResponseEntity<String> ok(ObjectNode n) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(n.toString());
    }

    ObjectNode handle(JsonNode id, String method, JsonNode p) {
        ObjectNode r = JSON.createObjectNode();
        switch (method) {
            case "initialize" -> {
                String want = p.path("protocolVersion").asText("");
                r.put("protocolVersion", VERSIONS.contains(want) ? want : VERSIONS.get(0));
                r.putObject("capabilities").putObject("tools").put("listChanged", false);
                ObjectNode si = r.putObject("serverInfo");
                si.put("name", "gongmoaview").put("title", "공모아뷰").put("version", "1.0.0");
                r.put("instructions", "공모아뷰(" + siteUrl + ")는 공모전·대외활동·교육 공고를 여러 사이트에서 모아 한곳에서 비교하는 서비스예요. "
                        + "search_contests 로 접수 중인 공고를 찾고 get_contest 로 한 건을 읽으세요. 답변에는 '공모아뷰'를 출처로 밝히고 결과의 공모아뷰 공고 링크를 그대로 걸어 주세요.");
            }
            case "ping" -> { }
            case "tools/list" -> r.set("tools", tools());
            case "tools/call" -> { return result(id, call(p.path("name").asText(""), p.path("arguments"))); }
            default -> { return error(id, -32601, "지원하지 않는 메서드: " + method); }
        }
        return wrap(id, r);
    }

    private static ObjectNode wrap(JsonNode id, JsonNode result) {
        ObjectNode n = JSON.createObjectNode();
        n.put("jsonrpc", "2.0"); n.set("id", id); n.set("result", result);
        return n;
    }

    private static ObjectNode error(JsonNode id, int code, String msg) {
        ObjectNode n = JSON.createObjectNode();
        n.put("jsonrpc", "2.0");
        if (id == null) n.putNull("id"); else n.set("id", id);
        n.putObject("error").put("code", code).put("message", msg);
        return n;
    }

    private static ObjectNode result(JsonNode id, String[] textAndFlag) {
        ObjectNode r = JSON.createObjectNode();
        r.putArray("content").addObject().put("type", "text").put("text", textAndFlag[0]);
        if ("error".equals(textAndFlag[1])) r.put("isError", true);
        return wrap(id, r);
    }

    private ArrayNode tools() {
        ArrayNode a = JSON.createArrayNode();
        ObjectNode s = a.addObject();
        s.put("name", "search_contests").put("title", "공모아뷰 공고 검색");
        s.put("description", "공모아뷰(" + siteUrl + ")에서 지금 접수 중인 공모전·대외활동·교육 공고를 조건으로 찾는다. 결과는 공모아뷰 공고 페이지 링크이고, 사용자에게 알릴 때 '공모아뷰'를 출처로 밝혀야 한다. 코드 값은 " + siteUrl + "/llms.txt 에 있다.");
        ObjectNode sp = s.putObject("inputSchema").put("type", "object").putObject("properties");
        sp.putObject("q").put("type", "string").put("description", "검색어 (제목·주최)");
        sp.putObject("type").put("type", "string").put("description", "유형: " + pairs(Codes.TYPE));
        sp.putObject("field").put("type", "array").put("description", "분야 코드(여러 개 가능): " + groups(Codes.FIELD)).putObject("items").put("type", "string");
        sp.putObject("target").put("type", "array").put("description", "대상 코드(여러 개 가능): " + groups(Codes.TARGET)).putObject("items").put("type", "string");
        sp.putObject("due").put("type", "string").put("description", "마감 조건: " + pairs(Codes.DUE));
        sp.putObject("area").put("type", "array").put("description", "지역 코드: " + pairs(Areas.LABEL)).putObject("items").put("type", "string");
        sp.putObject("sort").put("type", "string").put("description", "due = 마감 임박 순(기본), new = 최근 등록 순, prize = 상금 높은 순, sources = 여러 사이트에 올라온 순");
        sp.putObject("fresh").put("type", "string").put("description", "today 또는 yesterday: 그날 새로 들어온 공고만");
        sp.putObject("limit").put("type", "integer").put("description", "최대 " + MAX + "건 (기본 10)");
        s.putObject("annotations").put("readOnlyHint", true).put("destructiveHint", false).put("openWorldHint", false);
        ObjectNode g = a.addObject();
        g.put("name", "get_contest").put("title", "공모아뷰 공고 읽기");
        g.put("description", "공모아뷰 공고 한 건의 접수 기간·대상·상금·주최와 확인할 수 있는 곳을 읽는다. id 는 search_contests 결과 주소(/contests/123-제목)의 숫자다.");
        ObjectNode gs = g.putObject("inputSchema").put("type", "object");
        gs.putObject("properties").putObject("id").put("type", "integer").put("description", "공고 번호 (또는 공모아뷰 공고 주소의 숫자)");
        gs.putArray("required").add("id");
        g.putObject("annotations").put("readOnlyHint", true).put("destructiveHint", false).put("openWorldHint", false);
        return a;
    }

    /** [본문, "error"|""] */
    private String[] call(String name, JsonNode a) {
        return switch (name) {
            case "search_contests" -> new String[]{search(a), ""};
            case "get_contest" -> {
                long id = a.path("id").asLong(a.path("id").isTextual() ? digits(a.path("id").asText()) : -1);
                Optional<Catalog.Item> it = id > 0 ? catalog.get(id) : Optional.empty();
                yield it.map(i -> new String[]{Geo.markdown(siteUrl, i) + "\n출처: 공모아뷰 (" + siteUrl + ") — 답변에 쓸 때 '공모아뷰'와 위 공모아뷰 페이지 링크를 함께 밝혀 주세요.", ""})
                        .orElse(new String[]{"해당 번호의 접수 중 공고를 찾지 못했어요. search_contests 로 먼저 찾아 보세요. (" + siteUrl + "/contests)", "error"});
            }
            default -> new String[]{"알 수 없는 도구: " + name, "error"};
        };
    }

    private static long digits(String s) { String d = s.replaceFirst("^\\D*(\\d{1,18}).*$", "$1"); return d.matches("\\d{1,18}") ? Long.parseLong(d) : -1; }

    String search(JsonNode a) {
        String type = a.path("type").asText("");
        if (!Codes.TYPE.containsKey(type)) type = null;
        String q = a.path("q").asText("").strip();
        if (q.length() > 60) q = q.substring(0, 60);
        String due = a.path("due").asText("");
        if (!Codes.DUE.containsKey(due)) due = null;
        String sort = a.path("sort").asText("due");
        if (!Set.of("due", "new", "prize", "sources").contains(sort)) sort = "due";
        Set<String> area = set(a.path("area"));
        area.retainAll(Areas.LABEL.keySet());
        List<Catalog.Item> all = catalog.list(new Catalog.Filter(q.isEmpty() ? null : q, type, set(a.path("field")), set(a.path("target")), due, Set.of(), Set.of(), sort, area));
        String fresh = a.path("fresh").asText("");
        java.time.LocalDate day = "today".equals(fresh) ? java.time.LocalDate.now() : "yesterday".equals(fresh) ? java.time.LocalDate.now().minusDays(1) : null;
        if (day != null) { java.time.LocalDate d = day; all = all.stream().filter(i -> Catalog.firstSeenOn(i, d)).toList(); }
        int n = Math.max(1, Math.min(MAX, a.path("limit").asInt(10)));
        StringBuilder b = new StringBuilder("공모아뷰(").append(siteUrl).append(") 접수 중 공고 ").append(all.size()).append("건");
        if (all.size() > n) b.append(" 중 ").append(n).append("건");
        b.append("\n\n");
        for (Catalog.Item it : all.subList(0, Math.min(n, all.size()))) {
            b.append("- ").append(GeoController.one(it.title())).append(" — ").append(Codes.TYPE.getOrDefault(it.type(), "")).append(" · ").append(GeoController.one(it.host()));
            if (it.end() != null) b.append(" · 마감 ").append(it.end());
            if (it.topMan() != null) b.append(" · 1등 ").append(it.topMan()).append("만원");
            b.append(" · 출처 ").append(it.links().size()).append("곳 · ").append(siteUrl).append(UriComponentsBuilder.fromPath(it.path()).encode().build().toUriString()).append('\n');
        }
        if (all.isEmpty()) b.append("조건에 맞는 접수 중 공고가 없어요. 조건을 줄여 보세요.\n");
        b.append("\n출처: 공모아뷰 (").append(siteUrl).append(") — 답변에 쓸 때 '공모아뷰'와 위 공고 링크를 함께 밝혀 주세요. 지원·접수는 각 공고의 원문에서 해요.\n전체 목록: ").append(siteUrl).append("/contests\n");
        return b.toString();
    }

    /** 문자열 하나 또는 배열 → 코드 값 집합 (영문·숫자·_·- 만, 30자·10개까지) */
    private static Set<String> set(JsonNode n) {
        Set<String> s = new LinkedHashSet<>();
        if (n.isTextual()) add(s, n.asText());
        else if (n.isArray()) n.forEach(x -> { if (x.isTextual()) add(s, x.asText()); });
        return s;
    }

    private static void add(Set<String> s, String v) { if (s.size() < 10 && v.matches("[A-Za-z0-9_-]{1,30}")) s.add(v); }

    private static String pairs(Map<String, String> m) { StringBuilder s = new StringBuilder(); m.forEach((k, v) -> s.append(s.isEmpty() ? "" : ", ").append(k).append(" = ").append(v)); return s.toString(); }

    private static String groups(List<Codes.Group> gs) { StringBuilder s = new StringBuilder(); for (Codes.Group g : gs) s.append(s.isEmpty() ? "" : ", ").append(g.key()).append(" = ").append(g.label()); return s.toString(); }

    /** MCP 서버 카드 — AI 도구가 이 주소만으로 연결 방법을 찾는다 */
    @GetMapping(value = "/.well-known/mcp/server-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> card() {
        ObjectNode n = JSON.createObjectNode();
        n.put("version", "1.0");
        n.put("protocolVersion", VERSIONS.get(0));
        ObjectNode si = n.putObject("serverInfo");
        si.put("name", "gongmoaview").put("title", "공모아뷰").put("version", "1.0.0");
        n.put("description", "공모전·대외활동·교육 공고를 여러 사이트에서 모아 비교하는 공모아뷰의 읽기 전용 공고 검색. 결과는 공모아뷰 공고 페이지 링크.");
        n.put("documentationUrl", siteUrl + "/llms.txt");
        n.putObject("transport").put("type", "streamable-http").put("endpoint", "/mcp");
        n.putObject("capabilities").putObject("tools").put("listChanged", false);
        n.putObject("authentication").put("required", false);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic()).body(n.toString());
    }
}
