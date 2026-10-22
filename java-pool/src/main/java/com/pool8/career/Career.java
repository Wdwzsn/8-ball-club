package com.pool8.career;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 本机「我的生涯」数据：昵称、头像、人机战绩与成就，持久化到用户目录。 */
public final class Career {
    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".8ballclub");
    private static final Path FILE = DIR.resolve("career.txt");
    private static Career instance;

    public String nickname = "玩家";
    public int avatarIndex = 0;
    public int games;          // 人机对局总数
    public int wins;           // 人机胜场
    public int streak;         // 当前连赢
    public int bestStreak;     // 最长连赢
    public int totalPotted;    // 累计进球
    public int breakPots;      // 开球进球次数
    public final Set<String> unlocked = new LinkedHashSet<>();
    public final List<Achievement> achievements = Achievement.ALL;

    public static synchronized Career get() {
        if (instance == null) instance = load();
        return instance;
    }

    public double winRate() {
        return games == 0 ? 0 : wins / (double) games;
    }

    public int unlockedCount() {
        int n = 0;
        for (Achievement a : achievements) if (unlocked.contains(a.id)) n++;
        return n;
    }

    /** 记录一局人机对战结果（每局结束时调用一次）。 */
    public void recordAiGame(boolean win) {
        games++;
        if (win) {
            wins++;
            streak++;
            bestStreak = Math.max(bestStreak, streak);
        } else {
            streak = 0;
        }
        checkAndSave();
    }

    /** 每次击球进球后调用。 */
    public void addPotted(int n) {
        if (n <= 0) return;
        totalPotted += n;
        checkAndSave();
    }

    /** 开球即进球。 */
    public void addBreakPot() {
        breakPots++;
        checkAndSave();
    }

    private void checkAndSave() {
        for (Achievement a : achievements) {
            if (a.test(this)) unlocked.add(a.id);
        }
        save();
    }

    public void save() {
        try {
            Files.createDirectories(DIR);
            StringBuilder sb = new StringBuilder();
            sb.append("nickname=").append(escape(nickname)).append('\n');
            sb.append("avatar=").append(avatarIndex).append('\n');
            sb.append("games=").append(games).append('\n');
            sb.append("wins=").append(wins).append('\n');
            sb.append("streak=").append(streak).append('\n');
            sb.append("bestStreak=").append(bestStreak).append('\n');
            sb.append("totalPotted=").append(totalPotted).append('\n');
            sb.append("breakPots=").append(breakPots).append('\n');
            sb.append("unlocked=").append(String.join(",", unlocked)).append('\n');
            Files.write(FILE, sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
    }

    private static Career load() {
        Career c = new Career();
        try {
            if (Files.exists(FILE)) {
                for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String k = line.substring(0, eq).trim();
                    String v = line.substring(eq + 1).trim();
                    switch (k) {
                        case "nickname": c.nickname = unescape(v); break;
                        case "avatar": c.avatarIndex = parseInt(v, 0); break;
                        case "games": c.games = parseInt(v, 0); break;
                        case "wins": c.wins = parseInt(v, 0); break;
                        case "streak": c.streak = parseInt(v, 0); break;
                        case "bestStreak": c.bestStreak = parseInt(v, 0); break;
                        case "totalPotted": c.totalPotted = parseInt(v, 0); break;
                        case "breakPots": c.breakPots = parseInt(v, 0); break;
                        case "unlocked":
                            if (!v.isEmpty()) {
                                for (String id : v.split(",")) c.unlocked.add(id.trim());
                            }
                            break;
                        default: break;
                    }
                }
            }
        } catch (IOException ignored) {}
        return c;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("=", "\\=").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\r", "\r").replace("\\=", "=").replace("\\\\", "\\");
    }
}
