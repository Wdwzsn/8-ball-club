package com.pool8.career;

import java.util.List;
import java.util.function.Predicate;

/** 一项成就。 */
public final class Achievement {
    public final String id;
    public final String name;
    public final String desc;
    private final Predicate<Career> test;

    public Achievement(String id, String name, String desc, Predicate<Career> test) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.test = test;
    }

    public boolean test(Career c) {
        return test.test(c);
    }

    public static final List<Achievement> ALL = List.of(
            new Achievement("first_win", "首胜", "第一次战胜电脑", c -> c.wins >= 1),
            new Achievement("wins_5", "渐入佳境", "累计战胜电脑 5 次", c -> c.wins >= 5),
            new Achievement("wins_20", "老手", "累计战胜电脑 20 次", c -> c.wins >= 20),
            new Achievement("wins_100", "大师", "累计战胜电脑 100 次", c -> c.wins >= 100),
            new Achievement("streak_3", "三连胜", "连赢电脑 3 局", c -> c.bestStreak >= 3),
            new Achievement("streak_5", "五连胜", "连赢电脑 5 局", c -> c.bestStreak >= 5),
            new Achievement("break_pot", "开门红", "开球即进球", c -> c.breakPots >= 1),
            new Achievement("pot_50", "神射手", "累计打进 50 球", c -> c.totalPotted >= 50),
            new Achievement("pot_500", "百发百中", "累计打进 500 球", c -> c.totalPotted >= 500),
            new Achievement("games_10", "渐入佳境·局数", "完成 10 局人机对战", c -> c.games >= 10),
            new Achievement("winrate_50", "势均力敌", "至少 10 局且胜率过半", c -> c.games >= 10 && c.winRate() >= 0.5)
    );
}
