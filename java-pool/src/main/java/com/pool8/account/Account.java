package com.pool8.account;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * 账号会话：登录/注册走后端 HTTP API，token 保存在本机。
 * 默认服务器 http://127.0.0.1:8080（可用 LoginDialog 修改）。
 */
public final class Account {
    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".8ballclub");
    private static final Path FILE = DIR.resolve("account.txt");
    private static Account instance;

    private String token = "";
    private String username = "";
    private String server = "http://127.0.0.1:8080";

    public static synchronized Account get() {
        if (instance == null) instance = load();
        return instance;
    }

    public boolean loggedIn() { return !token.isEmpty() && !username.isEmpty(); }
    public String token() { return token; }
    public String username() { return username; }
    public String server() { return server; }
    public void setServer(String s) { server = s.trim(); }

    public void login(String user, String pass) throws Exception {
        String body = "{\"username\":\"" + esc(user) + "\",\"password\":\"" + esc(pass) + "\"}";
        String resp = post("/api/login", body);
        apply(resp);
    }

    public void register(String user, String pass) throws Exception {
        String body = "{\"username\":\"" + esc(user) + "\",\"password\":\"" + esc(pass) + "\"}";
        String resp = post("/api/register", body);
        apply(resp);
    }

    private void apply(String resp) throws Exception {
        String tok = str(resp, "token");
        if (tok.isEmpty()) {
            String err = str(resp, "error");
            throw new Exception(err.isEmpty() ? "登录失败（请确认服务器已启动）" : err);
        }
        token = tok;
        username = str(resp, "user");
        save();
    }

    public void logout() {
        token = "";
        username = "";
        save();
    }

    private String post(String path, String body) throws Exception {
        HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(server + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> r = c.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return r.body() == null ? "" : r.body();
    }

    private void save() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, "token=" + esc(token) + "\nuser=" + esc(username) + "\nserver=" + esc(server) + "\n",
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }

    private static Account load() {
        Account a = new Account();
        try {
            if (Files.exists(FILE)) {
                for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String k = line.substring(0, eq).trim();
                    String v = unesc(line.substring(eq + 1).trim());
                    if (k.equals("token")) a.token = v;
                    else if (k.equals("user")) a.username = v;
                    else if (k.equals("server") && !v.isEmpty()) a.server = v;
                }
            }
        } catch (Exception ignored) {}
        return a;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "");
    }

    private static String unesc(String s) {
        return s.replace("\\n", "").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** 从简单 JSON 里取字符串字段（够用即可）。 */
    private static String str(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int colon = json.indexOf(':', i + key.length() + 2);
        if (colon < 0) return "";
        int q = json.indexOf('"', colon + 1);
        if (q < 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int k = q + 1; k < json.length(); k++) {
            char ch = json.charAt(k);
            if (ch == '\\') {
                if (k + 1 < json.length()) sb.append(json.charAt(++k));
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
